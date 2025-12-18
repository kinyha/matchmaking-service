package com.matchmaking.model;

public enum AssignmentType {
    PRIMARY(0),
    SECONDARY(50),
    AUTOFILL(100);

    private final int mmrPenalty;

    AssignmentType(int mmrPenalty) {
        this.mmrPenalty = mmrPenalty;
    }

    public int getMmrPenalty() {
        return mmrPenalty;
    }
}
