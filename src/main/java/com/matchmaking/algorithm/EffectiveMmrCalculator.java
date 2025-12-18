package com.matchmaking.algorithm;

import com.matchmaking.model.AssignmentType;
import com.matchmaking.model.Player;
import com.matchmaking.model.Role;

public class EffectiveMmrCalculator {

    public int calculate(Player player, Role assignedRole, AssignmentType assignmentType) {
        return player.mmr() - assignmentType.getMmrPenalty();
    }

    public AssignmentType determineAssignmentType(Player player, Role assignedRole) {
        if (assignedRole == player.primaryRole()) {
            return AssignmentType.PRIMARY;
        } else if (assignedRole == player.secondaryRole()) {
            return AssignmentType.SECONDARY;
        } else {
            return AssignmentType.AUTOFILL;
        }
    }
}
