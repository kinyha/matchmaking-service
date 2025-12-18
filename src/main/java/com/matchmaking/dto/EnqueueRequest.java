package com.matchmaking.dto;

import com.matchmaking.model.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnqueueRequest(
        @NotBlank String playerId,
        @NotBlank String displayName,
        @Min(0) @Max(3000) int mmr,
        @NotNull Role primaryRole,
        @NotNull Role secondaryRole
) {}
