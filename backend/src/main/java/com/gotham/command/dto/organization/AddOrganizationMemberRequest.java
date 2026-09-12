package com.gotham.command.dto.organization;

import java.util.UUID;

import com.gotham.command.entity.OrganizationRole;

import jakarta.validation.constraints.NotNull;

public record AddOrganizationMemberRequest(
    @NotNull UUID userId,
    @NotNull OrganizationRole role
) {}
