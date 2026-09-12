package com.gotham.command.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(min = 2, max = 32) @Pattern(regexp = "^[A-Z0-9]+$", message = "Project key must be uppercase alphanumeric") String projectKey,
    String description
) {}
