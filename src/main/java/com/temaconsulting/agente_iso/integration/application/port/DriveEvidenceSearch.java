package com.temaconsulting.agente_iso.integration.application.port;

import java.util.List;

/** Finds documentary evidence below the Drive folder configured for a service. */
public interface DriveEvidenceSearch {
    SearchResult search(SearchRequest request);

    record SearchRequest(String rootFolderId, Kind kind, String officialNomenclature, Cardinality cardinality) {
    }

    enum Kind {
        DOCUMENT,
        FOLDER
    }

    enum Cardinality {
        ONE,
        MULTIPLE
    }

    enum Outcome {
        FOUND,
        ABSENT,
        AMBIGUOUS,
        NOT_CHECKABLE
    }

    enum Problem {
        NOT_CONFIGURED,
        MISSING_FOLDER,
        INVALID_EXPECTATION,
        AUTH_ERROR,
        PERMISSION_ERROR,
        UNAVAILABLE,
        INCOMPLETE
    }

    record Match(String externalId, String name, Kind kind, String webUrl, String relativePath) {
    }

    record SearchResult(Outcome outcome, Problem problem, List<Match> matches) {
        public SearchResult {
            matches = List.copyOf(matches);
        }

        public static SearchResult found(List<Match> matches, Cardinality cardinality) {
            Outcome outcome = cardinality == Cardinality.ONE && matches.size() > 1
                    ? Outcome.AMBIGUOUS : Outcome.FOUND;
            return new SearchResult(outcome, null, matches);
        }

        public static SearchResult absent() {
            return new SearchResult(Outcome.ABSENT, null, List.of());
        }

        public static SearchResult notCheckable(Problem problem) {
            return new SearchResult(Outcome.NOT_CHECKABLE, problem, List.of());
        }
    }
}
