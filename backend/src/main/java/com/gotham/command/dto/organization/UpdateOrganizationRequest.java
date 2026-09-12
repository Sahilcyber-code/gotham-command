package com.gotham.command.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
    @NotBlank @Size(max = 255) String name,
    String description
) {}
