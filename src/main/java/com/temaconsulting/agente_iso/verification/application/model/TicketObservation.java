package com.temaconsulting.agente_iso.verification.application.model;

import java.time.Instant;

public record TicketObservation(Instant observedAt, String snapshotJson) { }
