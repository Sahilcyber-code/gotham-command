package com.gotham.command.dto.project;

import com.gotham.command.entity.ProjectRole;

import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRequest(
    @NotNull ProjectRole role
) {}
