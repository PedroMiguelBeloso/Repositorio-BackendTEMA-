package com.temaconsulting.agente_iso.integration.application;

import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.IntegrationStatus;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationHealthServiceTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T14:30:00Z"));

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void startsWithoutConnectorsAndNeverInventsAvailability() {
        FakeProbe drive = new FakeProbe(IntegrationSource.DRIVE, false, () -> {
            throw new AssertionError("Unconfigured Drive must not be read");
        });
        FakeProbe redmine = new FakeProbe(IntegrationSource.REDMINE, false, () -> {
            throw new AssertionError("Unconfigured Redmine must not be read");
        });
        IntegrationHealthService service = service(drive, redmine, "PT1S");

        assertEquals(List.of(SourceHealth.NOT_CONFIGURED, SourceHealth.NOT_CONFIGURED),
                service.knownStatuses().stream().map(IntegrationStatus::status).toList());
        assertEquals(List.of(SourceHealth.NOT_CONFIGURED, SourceHealth.NOT_CONFIGURED),
                service.testAll().stream().map(IntegrationStatus::status).toList());
        assertEquals(0, drive.reads.get());
        assertEquals(0, redmine.reads.get());
    }

    @Test
    void testPerformsNewReadsAndUpdatesCachedTimestamp() {
        FakeProbe drive = new FakeProbe(IntegrationSource.DRIVE, true, () -> {});
        FakeProbe redmine = new FakeProbe(IntegrationSource.REDMINE, true, () -> {});
        IntegrationHealthService service = service(drive, redmine, "PT1S");
        assertEquals(SourceHealth.UNAVAILABLE, service.knownStatuses().getFirst().status());
        assertEquals(0, drive.reads.get());

        clock.current = Instant.parse("2026-10-03T14:31:00Z");
        List<IntegrationStatus> result = service.testAll();

        assertEquals(List.of(IntegrationSource.DRIVE, IntegrationSource.REDMINE),
                result.stream().map(IntegrationStatus::source).toList());
        assertEquals(List.of(SourceHealth.AVAILABLE, SourceHealth.AVAILABLE),
                result.stream().map(IntegrationStatus::status).toList());
        assertEquals(clock.current, result.getFirst().checkedAt());
        assertSame(result, service.knownStatuses());
        assertEquals(1, drive.reads.get());
        assertEquals(1, redmine.reads.get());
        service.knownStatuses();
        assertEquals(1, drive.reads.get());

        clock.current = Instant.parse("2026-10-03T14:32:00Z");
        assertEquals(clock.current, service.testAll().getFirst().checkedAt());
        assertEquals(2, drive.reads.get());
    }

    @Test
    void expectedRemoteFailuresAreIndependentAndMessagesDoNotLeakSecrets() {
        String secret = "Bearer secret-token https://private.example/issues";
        for (SourceHealth failure : List.of(SourceHealth.AUTH_ERROR,
                SourceHealth.PERMISSION_ERROR, SourceHealth.UNAVAILABLE)) {
            FakeProbe drive = new FakeProbe(IntegrationSource.DRIVE, true,
                    () -> { throw new SourceProbeException(failure, new IllegalStateException(secret)); });
            FakeProbe redmine = new FakeProbe(IntegrationSource.REDMINE, true, () -> {});
            IntegrationHealthService service = service(drive, redmine, "PT1S");

            List<IntegrationStatus> result = service.testAll();
            assertEquals(failure, result.get(0).status());
            assertEquals(SourceHealth.AVAILABLE, result.get(1).status());
            assertFalse(result.toString().contains(secret));
            assertEquals(1, redmine.reads.get());

            FakeProbe failedRedmine = new FakeProbe(IntegrationSource.REDMINE, true,
                    () -> { throw new SourceProbeException(failure, new IllegalStateException(secret)); });
            FakeProbe successfulDrive = new FakeProbe(IntegrationSource.DRIVE, true, () -> {});
            result = service(successfulDrive, failedRedmine, "PT1S").testAll();
            assertEquals(SourceHealth.AVAILABLE, result.get(0).status());
            assertEquals(failure, result.get(1).status());
            assertEquals(1, successfulDrive.reads.get());
        }
    }

    @Test
    void aSlowSourceTimesOutWhileTheOtherResultIsReturned() {
        FakeProbe drive = new FakeProbe(IntegrationSource.DRIVE, true, () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        FakeProbe redmine = new FakeProbe(IntegrationSource.REDMINE, true, () -> {});
        IntegrationHealthService service = service(drive, redmine, "PT0.02S");

        List<IntegrationStatus> result = service.testAll();

        assertEquals(SourceHealth.UNAVAILABLE, result.get(0).status());
        assertEquals(SourceHealth.AVAILABLE, result.get(1).status());
        assertEquals(1, redmine.reads.get());
    }

    @Test
    void unexpectedBackendFailureIsNotDisguisedAsRemoteUnavailability() {
        IllegalStateException bug = new IllegalStateException("backend bug");
        IntegrationHealthService service = service(
                new FakeProbe(IntegrationSource.DRIVE, true, () -> { throw bug; }),
                new FakeProbe(IntegrationSource.REDMINE, true, () -> {}), "PT1S");

        ApiException failure = assertThrows(ApiException.class, service::testAll);
        assertEquals(ProblemCode.INTERNAL_ERROR, failure.code());
        assertFalse(failure.safeMessage().contains(bug.getMessage()));
    }

    @Test
    void timeoutConfigurationMustBeBounded() {
        FakeProbe drive = new FakeProbe(IntegrationSource.DRIVE, false, () -> {});
        FakeProbe redmine = new FakeProbe(IntegrationSource.REDMINE, false, () -> {});
        assertThrows(IllegalArgumentException.class,
                () -> service(drive, redmine, "PT0S"));
        assertThrows(IllegalArgumentException.class,
                () -> service(drive, redmine, "PT31S"));
    }

    private IntegrationHealthService service(FakeProbe drive, FakeProbe redmine, String timeout) {
        return new IntegrationHealthService(List.of(drive, redmine), clock, executor, timeout);
    }

    private static final class FakeProbe implements IntegrationProbe {
        private final IntegrationSource source;
        private final boolean configured;
        private final Runnable read;
        private final AtomicInteger reads = new AtomicInteger();

        private FakeProbe(IntegrationSource source, boolean configured, Runnable read) {
            this.source = source;
            this.configured = configured;
            this.read = read;
        }

        @Override
        public IntegrationSource source() {
            return source;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public void checkReadable() {
            reads.incrementAndGet();
            read.run();
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant current;

        private MutableClock(Instant initial) {
            current = initial;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
