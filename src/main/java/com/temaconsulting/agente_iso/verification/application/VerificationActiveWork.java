package com.temaconsulting.agente_iso.verification.application;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The single-instance worker exposes the item it is currently evaluating. */
@Component
public class VerificationActiveWork {
    private final ConcurrentHashMap<UUID, Item> current = new ConcurrentHashMap<>();

    public void begin(UUID runId, UUID expectationId, String ticketId) {
        current.put(runId, new Item(expectationId, ticketId));
    }

    public Optional<Item> current(UUID runId) {
        return Optional.ofNullable(current.get(runId));
    }

    public void clear(UUID runId) {
        current.remove(runId);
    }

    public record Item(UUID expectationId, String ticketId) { }
}
