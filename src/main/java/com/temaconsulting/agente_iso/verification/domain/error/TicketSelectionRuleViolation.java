package com.temaconsulting.agente_iso.verification.domain.error;

/** Business-rule violation while deriving the single ticket selector for a macroactivity. */
public class TicketSelectionRuleViolation extends RuntimeException {
    public TicketSelectionRuleViolation(String message) {
        super(message);
    }
}
