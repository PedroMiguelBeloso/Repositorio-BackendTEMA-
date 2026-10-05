package com.temaconsulting.agente_iso.integration.infrastructure;

import com.temaconsulting.agente_iso.integration.application.DriveFolderCatalog;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.IntegrationProbe;
import com.temaconsulting.agente_iso.integration.application.SourceProbeException;
import com.temaconsulting.agente_iso.integration.domain.ExternalFolder;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class GoogleDriveAdapter implements DriveFolderCatalog, DriveEvidenceSearch, IntegrationProbe {
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String SHORTCUT_MIME = "application/vnd.google-apps.shortcut";
    private static final String FOLDER_QUERY = "mimeType = '" + FOLDER_MIME + "' and trashed = false";
    private static final String LIST_FIELDS =
            "nextPageToken,incompleteSearch,files(id,name,mimeType,parents,webViewLink,trashed)";
    private static final String FILE_FIELDS = "id,name,mimeType,parents,webViewLink,trashed";
    private static final int DRIVE_PAGE_SIZE = 1000;
    private static final int MAX_DRIVE_PAGES = 100;
    private static final int MAX_EVIDENCE_FOLDERS = 1000;
    private static final int MAX_EVIDENCE_ITEMS = 100000;
    private static final Duration MAX_EVIDENCE_SEARCH_TIME = Duration.ofSeconds(30);

    private final boolean enabled;
    private final DriveAccessTokenProvider tokens;
    private final RestClient client;

    public GoogleDriveAdapter(
            RestClient.Builder builder,
            DriveAccessTokenProvider tokens,
            @Value("${tema.drive.enabled:false}") boolean enabled,
            @Value("${tema.drive.api-base-url:https://www.googleapis.com/drive/v3}") String baseUrl,
            @Value("${tema.drive.timeout:PT5S}") String timeoutText
    ) {
        this.enabled = enabled;
        this.tokens = tokens;
        Duration timeout = Duration.parse(timeoutText);
        if (timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Drive timeout must be between 1 ms and 30 s");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeout);
        requests.setReadTimeout(timeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(requests).build();
    }

    @Override
    public IntegrationSource source() {
        return IntegrationSource.DRIVE;
    }

    @Override
    public boolean isConfigured() {
        return enabled;
    }

    @Override
    public void checkReadable() {
        if (!enabled) {
            throw new SourceProbeException(SourceHealth.UNAVAILABLE,
                    new IllegalStateException("Drive is disabled"));
        }
        try {
            DriveListResponse response = listPage(null, 1);
            if (response == null || Boolean.TRUE.equals(response.incompleteSearch())) {
                throw new DriveReadException(SourceHealth.UNAVAILABLE,
                        new IllegalStateException("Incomplete Drive read"));
            }
        } catch (DriveReadException ex) {
            throw new SourceProbeException(ex.health(), ex);
        }
    }

    @Override
    public Page<ExternalFolder> search(String query, int page, int size) {
        if (page < 0) throw invalid("page", "La página debe ser mayor o igual a cero");
        if (size < 1 || size > 100) throw invalid("size", "El tamaño debe estar entre 1 y 100");
        String filter = query == null ? "" : query.trim();
        if (filter.length() > 200) throw invalid("query", "La búsqueda es demasiado larga");
        requireConfigured();
        try {
            List<DriveFile> files = allFolders();
            Map<String, DriveFile> cache = new HashMap<>();
            files.forEach(file -> cache.put(file.id(), file));
            String needle = filter.toLowerCase(Locale.ROOT);
            List<ExternalFolder> matches = new ArrayList<>();
            for (DriveFile file : files) {
                if (file.name() != null && file.name().toLowerCase(Locale.ROOT).contains(needle)) {
                    matches.add(toFolder(file, cache));
                }
            }
            matches.sort(Comparator.comparing(ExternalFolder::path).thenComparing(ExternalFolder::id));
            long from = (long) page * size;
            if (from >= matches.size()) return new Page<>(List.of(), page, size, matches.size());
            int start = (int) from;
            return new Page<>(matches.subList(start, Math.min(start + size, matches.size())),
                    page, size, matches.size());
        } catch (DriveReadException ex) {
            throw sourceFailure(ex);
        }
    }

    @Override
    public ExternalFolder findById(String folderId) {
        if (folderId == null || folderId.isBlank()) return null;
        if (!folderId.trim().matches("[A-Za-z0-9_-]{1,255}")) return null;
        requireConfigured();
        try {
            DriveFile file = getFile(folderId.trim(), true);
            if (file == null || file.id() == null || file.name() == null || file.name().isBlank()
                    || !FOLDER_MIME.equals(file.mimeType())
                    || Boolean.TRUE.equals(file.trashed())) return null;
            return toFolder(file, new HashMap<>());
        } catch (DriveReadException ex) {
            throw sourceFailure(ex);
        }
    }

    @Override
    public SearchResult search(SearchRequest request) {
        if (request == null || request.kind() == null || request.cardinality() == null
                || request.officialNomenclature() == null || request.officialNomenclature().isBlank()) {
            return SearchResult.notCheckable(Problem.INVALID_EXPECTATION);
        }
        String nomenclature = request.officialNomenclature().trim();
        String[] expectedPath = nomenclature.split("/", -1);
        for (String segment : expectedPath) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                return SearchResult.notCheckable(Problem.INVALID_EXPECTATION);
            }
        }
        if (request.rootFolderId() == null
                || !request.rootFolderId().matches("[A-Za-z0-9_-]{1,255}")) {
            return SearchResult.notCheckable(Problem.MISSING_FOLDER);
        }
        if (!enabled) return SearchResult.notCheckable(Problem.NOT_CONFIGURED);

        try {
            DriveFile root = getFile(request.rootFolderId(), true);
            if (root == null || !request.rootFolderId().equals(root.id())
                    || !FOLDER_MIME.equals(root.mimeType()) || Boolean.TRUE.equals(root.trashed())) {
                return SearchResult.notCheckable(Problem.MISSING_FOLDER);
            }
            return searchWithinRoot(root.id(), request, nomenclature, expectedPath);
        } catch (DriveReadException ex) {
            return SearchResult.notCheckable(switch (ex.health()) {
                case AUTH_ERROR -> Problem.AUTH_ERROR;
                case PERMISSION_ERROR -> Problem.PERMISSION_ERROR;
                default -> Problem.UNAVAILABLE;
            });
        }
    }

    private SearchResult searchWithinRoot(
            String rootId, SearchRequest request, String nomenclature, String[] expectedPath
    ) {
        Deque<SearchFolder> pending = new ArrayDeque<>();
        Set<String> enqueued = new HashSet<>();
        Set<String> matchedIds = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        pending.addLast(new SearchFolder(rootId, ""));
        enqueued.add(rootId);
        int pages = 0;
        int items = 0;
        long startedAt = System.nanoTime();

        while (!pending.isEmpty()) {
            SearchFolder folder = pending.removeFirst();
            Set<String> pageTokens = new HashSet<>();
            String next = null;
            do {
                if (pages >= MAX_DRIVE_PAGES || expired(startedAt)) {
                    return SearchResult.notCheckable(Problem.INCOMPLETE);
                }
                DriveListResponse response = listPage(childrenQuery(folder.id()), next, DRIVE_PAGE_SIZE);
                pages++;
                if (response == null || response.files() == null
                        || Boolean.TRUE.equals(response.incompleteSearch())) {
                    return SearchResult.notCheckable(Problem.INCOMPLETE);
                }
                for (DriveFile child : response.files()) {
                    if (++items > MAX_EVIDENCE_ITEMS || expired(startedAt)) {
                        return SearchResult.notCheckable(Problem.INCOMPLETE);
                    }
                    if (child == null || child.id() == null
                            || !child.id().matches("[A-Za-z0-9_-]{1,255}")
                            || child.name() == null || child.mimeType() == null
                            || child.parents() == null) {
                        return SearchResult.notCheckable(Problem.INCOMPLETE);
                    }
                    if (Boolean.TRUE.equals(child.trashed()) || !child.parents().contains(folder.id())) {
                        continue;
                    }
                    boolean isFolder = FOLDER_MIME.equals(child.mimeType());
                    String relativePath = folder.path().isEmpty()
                            ? child.name() : folder.path() + "/" + child.name();
                    if (isFolder && enqueued.add(child.id())) {
                        if (enqueued.size() > MAX_EVIDENCE_FOLDERS) {
                            return SearchResult.notCheckable(Problem.INCOMPLETE);
                        }
                        pending.addLast(new SearchFolder(child.id(), relativePath));
                    }
                    boolean kindMatches = request.kind() == Kind.FOLDER ? isFolder
                            : !isFolder && !SHORTCUT_MIME.equals(child.mimeType());
                    boolean nameMatches = expectedPath.length == 1
                            ? child.name().equals(nomenclature)
                            : relativePath.equals(nomenclature);
                    if (kindMatches && nameMatches && matchedIds.add(child.id())) {
                        matches.add(new Match(child.id(), child.name(), request.kind(),
                                child.webViewLink(), relativePath));
                    }
                }
                next = response.nextPageToken();
                if (next != null && !next.isBlank() && !pageTokens.add(next)) {
                    return SearchResult.notCheckable(Problem.INCOMPLETE);
                }
            } while (next != null && !next.isBlank());
        }
        if (matches.isEmpty()) return SearchResult.absent();
        matches.sort(Comparator.comparing(Match::relativePath).thenComparing(Match::externalId));
        return SearchResult.found(matches, request.cardinality());
    }

    private static boolean expired(long startedAt) {
        return System.nanoTime() - startedAt >= MAX_EVIDENCE_SEARCH_TIME.toNanos();
    }

    private static String childrenQuery(String folderId) {
        // IDs are validated before traversal and come from Drive for descendant folders.
        return "'" + folderId + "' in parents and trashed = false";
    }

    private void requireConfigured() {
        if (!enabled) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "La conexión de Google Drive no está configurada", List.of());
        }
    }

    private List<DriveFile> allFolders() {
        List<DriveFile> result = new ArrayList<>();
        Set<String> tokensSeen = new HashSet<>();
        String next = null;
        for (int count = 0; count < MAX_DRIVE_PAGES; count++) {
            DriveListResponse response = listPage(next, DRIVE_PAGE_SIZE);
            if (response == null || Boolean.TRUE.equals(response.incompleteSearch())) {
                throw new DriveReadException(SourceHealth.UNAVAILABLE,
                        new IllegalStateException("Incomplete Drive folder listing"));
            }
            if (response.files() != null) {
                for (DriveFile file : response.files()) {
                    if (file.id() != null && file.name() != null
                            && FOLDER_MIME.equals(file.mimeType())
                            && !Boolean.TRUE.equals(file.trashed())) result.add(file);
                }
            }
            next = response.nextPageToken();
            if (next == null || next.isBlank()) return result;
            if (!tokensSeen.add(next)) break;
        }
        throw new DriveReadException(SourceHealth.UNAVAILABLE,
                new IllegalStateException("Drive pagination did not complete"));
    }

    private DriveListResponse listPage(String pageToken, int pageSize) {
        return listPage(FOLDER_QUERY, pageToken, pageSize);
    }

    private DriveListResponse listPage(String query, String pageToken, int pageSize) {
        try {
            return client.get().uri(uri -> {
                        uri.path("/files")
                                .queryParam("q", query)
                                .queryParam("fields", LIST_FIELDS)
                                .queryParam("pageSize", pageSize)
                                .queryParam("supportsAllDrives", true)
                                .queryParam("includeItemsFromAllDrives", true);
                        if (pageToken != null) uri.queryParam("pageToken", pageToken);
                        return uri.build();
                    }).headers(headers -> headers.setBearerAuth(tokens.accessToken()))
                    .retrieve().body(DriveListResponse.class);
        } catch (DriveReadException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    private DriveFile getFile(String id, boolean missingIsNull) {
        try {
            return client.get().uri(uri -> uri.path("/files/{id}")
                            .queryParam("fields", FILE_FIELDS)
                            .queryParam("supportsAllDrives", true)
                            .build(id))
                    .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
                    .retrieve().body(DriveFile.class);
        } catch (RestClientResponseException ex) {
            if (missingIsNull && ex.getStatusCode().value() == 404) return null;
            throw translate(ex);
        } catch (DriveReadException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    private ExternalFolder toFolder(DriveFile file, Map<String, DriveFile> cache) {
        return new ExternalFolder(file.id(), file.name(), path(file, cache, new HashSet<>(), 0),
                file.webViewLink(), firstParent(file));
    }

    private String path(DriveFile file, Map<String, DriveFile> cache, Set<String> seen, int depth) {
        if (depth >= 20 || !seen.add(file.id())) return "/" + file.name();
        String parentId = firstParent(file);
        if (parentId == null) return "/" + file.name();
        DriveFile parent = cache.get(parentId);
        if (parent == null) {
            parent = getFile(parentId, true);
            if (parent == null) return "/" + file.name();
            cache.put(parentId, parent);
        }
        if (!FOLDER_MIME.equals(parent.mimeType()) || parent.name() == null) return "/" + file.name();
        return path(parent, cache, seen, depth + 1) + "/" + file.name();
    }

    private static String firstParent(DriveFile file) {
        return file.parents() == null || file.parents().isEmpty() ? null : file.parents().getFirst();
    }

    private static DriveReadException translate(RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            SourceHealth health = switch (response.getStatusCode().value()) {
                case 401 -> SourceHealth.AUTH_ERROR;
                case 403 -> SourceHealth.PERMISSION_ERROR;
                default -> SourceHealth.UNAVAILABLE;
            };
            return new DriveReadException(health, ex);
        }
        return new DriveReadException(SourceHealth.UNAVAILABLE, ex);
    }

    private static ApiException sourceFailure(DriveReadException ex) {
        ProblemCode code = ex.health() == SourceHealth.PERMISSION_ERROR
                ? ProblemCode.SOURCE_PERMISSION_DENIED : ProblemCode.SOURCE_UNAVAILABLE;
        String message = ex.health() == SourceHealth.PERMISSION_ERROR
                ? "No hay permiso para consultar Google Drive"
                : "No se pudo consultar Google Drive";
        return new ApiException(code, HttpStatus.SERVICE_UNAVAILABLE, message, List.of());
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                "La solicitud es inválida", List.of(new FieldProblem(field, message)));
    }

    private record DriveListResponse(List<DriveFile> files, String nextPageToken, Boolean incompleteSearch) {
    }

    private record DriveFile(String id, String name, String mimeType, List<String> parents,
                             String webViewLink, Boolean trashed) {
    }

    private record SearchFolder(String id, String path) {
    }
}
