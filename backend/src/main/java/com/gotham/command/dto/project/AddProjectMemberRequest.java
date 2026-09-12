package com.gotham.command.dto.project;

import java.util.UUID;

import com.gotham.command.entity.ProjectRole;

import jakarta.validation.constraints.NotNull;

public record AddProjectMemberRequest(
    @NotNull UUID userId,
    @NotNull ProjectRole role
) {}
