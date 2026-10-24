package com.temaconsulting.agente_iso.integration.application;

import com.temaconsulting.agente_iso.integration.application.port.IntegrationProbe;
import com.temaconsulting.agente_iso.integration.application.error.SourceProbeException;

import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.IntegrationStatus;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IntegrationHealthService {
    private final Map<IntegrationSource, IntegrationProbe> probes;
    private final Clock clock;
    private final ExecutorService executor;
    private final Duration timeout;
    private final AtomicReference<List<IntegrationStatus>> knownStatuses;

    public IntegrationHealthService(
            List<IntegrationProbe> probes,
            Clock clock,
            @Qualifier("integrationProbeExecutor") ExecutorService executor,
            @Value("${tema.integrations.probe-timeout:PT3S}") String timeoutText
    ) {
        this.probes = indexProbes(probes);
        this.clock = clock;
        this.executor = executor;
        this.timeout = Duration.parse(timeoutText);
        if (timeout.compareTo(Duration.ofSeconds(30)) > 0 || timeout.toMillis() < 1) {
            throw new IllegalArgumentException("Integration probe timeout must be between 1 ms and 30 s");
        }

        Instant checkedAt = clock.instant();
        List<IntegrationStatus> initial = new ArrayList<>();
        for (IntegrationSource source : IntegrationSource.values()) {
            SourceHealth state = this.probes.get(source).isConfigured()
                    ? SourceHealth.UNAVAILABLE : SourceHealth.NOT_CONFIGURED;
            initial.add(state == SourceHealth.UNAVAILABLE
                    ? new IntegrationStatus(source, state, checkedAt,
                    "Todavía no se realizó una prueba de lectura de " + source.displayName())
                    : status(source, state, checkedAt));
        }
        this.knownStatuses = new AtomicReference<>(List.copyOf(initial));
    }

    public List<IntegrationStatus> knownStatuses() {
        return knownStatuses.get();
    }

    public List<IntegrationStatus> testAll() {
        Instant checkedAt = clock.instant();
        Map<IntegrationSource, Future<IntegrationStatus>> pending = new EnumMap<>(IntegrationSource.class);
        Map<IntegrationSource, IntegrationStatus> immediate = new EnumMap<>(IntegrationSource.class);

        // Launch both checks before awaiting either one so a slow source cannot prevent
        // the other source from being checked.
        for (IntegrationSource source : IntegrationSource.values()) {
            try {
                IntegrationProbe probe = probes.get(source);
                pending.put(source, executor.submit(() -> check(probe, checkedAt)));
            } catch (RejectedExecutionException ex) {
                immediate.put(source, status(source, SourceHealth.UNAVAILABLE, checkedAt));
            }
        }

        List<IntegrationStatus> results = new ArrayList<>();
        try {
            for (IntegrationSource source : IntegrationSource.values()) {
                IntegrationStatus result = immediate.get(source);
                results.add(result != null ? result : await(source, pending.get(source), checkedAt));
            }
        } catch (RuntimeException ex) {
            pending.values().forEach(future -> future.cancel(true));
            throw ex;
        }
        List<IntegrationStatus> snapshot = List.copyOf(results);
        knownStatuses.set(snapshot);
        return snapshot;
    }

    private IntegrationStatus check(IntegrationProbe probe, Instant checkedAt) {
        IntegrationSource source = probe.source();
        if (!probe.isConfigured()) {
            return status(source, SourceHealth.NOT_CONFIGURED, checkedAt);
        }
        try {
            probe.checkReadable();
            return status(source, SourceHealth.AVAILABLE, checkedAt);
        } catch (SourceProbeException ex) {
            return status(source, ex.status(), checkedAt);
        }
    }

    private IntegrationStatus await(IntegrationSource source, Future<IntegrationStatus> pending, Instant checkedAt) {
        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            pending.cancel(true);
            return status(source, SourceHealth.UNAVAILABLE, checkedAt);
        } catch (InterruptedException ex) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw unexpected();
        } catch (ExecutionException ex) {
            throw unexpected();
        }
    }

    private static ApiException unexpected() {
        return new ApiException(ProblemCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno al comprobar las integraciones", List.of());
    }

    private static Map<IntegrationSource, IntegrationProbe> indexProbes(List<IntegrationProbe> probes) {
        Map<IntegrationSource, IntegrationProbe> indexed = new EnumMap<>(IntegrationSource.class);
        for (IntegrationProbe probe : probes) {
            if (indexed.putIfAbsent(probe.source(), probe) != null) {
                throw new IllegalStateException("Duplicate integration probe: " + probe.source());
            }
        }
        for (IntegrationSource source : IntegrationSource.values()) {
            if (!indexed.containsKey(source)) {
                throw new IllegalStateException("Missing integration probe: " + source);
            }
        }
        return Map.copyOf(indexed);
    }

    private static IntegrationStatus status(IntegrationSource source, SourceHealth state, Instant checkedAt) {
        String name = source.displayName();
        String message = switch (state) {
            case AVAILABLE -> "Consulta de lectura de " + name + " realizada correctamente";
            case AUTH_ERROR -> "No se pudo autenticar la conexión de " + name;
            case PERMISSION_ERROR -> "La conexión de " + name + " no tiene permiso de lectura";
            case UNAVAILABLE -> "No se pudo completar la consulta de " + name;
            case NOT_CONFIGURED -> "La conexión de " + name + " no está configurada";
        };
        return new IntegrationStatus(source, state, checkedAt, message);
    }
}
