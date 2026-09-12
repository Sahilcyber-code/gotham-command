package com.gotham.command.dto.organization;

import com.gotham.command.entity.OrganizationRole;

import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationMemberRequest(
    @NotNull OrganizationRole role
) {}
