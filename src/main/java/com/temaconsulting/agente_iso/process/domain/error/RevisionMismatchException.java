package com.temaconsulting.agente_iso.process.domain.error;

public class RevisionMismatchException extends RuntimeException {
    private final int expectedRevision;
    private final int currentRevision;

    public RevisionMismatchException(int expectedRevision, int currentRevision) {
        super("Process version revision changed: expected " + expectedRevision + ", current " + currentRevision);
        this.expectedRevision = expectedRevision;
        this.currentRevision = currentRevision;
    }

    public int expectedRevision() {
        return expectedRevision;
    }

    public int currentRevision() {
        return currentRevision;
    }
}
