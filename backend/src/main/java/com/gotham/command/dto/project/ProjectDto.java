package com.gotham.command.dto.project;

import java.time.Instant;
import java.util.UUID;

import com.gotham.command.entity.Project;

public record ProjectDto(
    UUID id,
    UUID organizationId,
    String name,
    String projectKey,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
    public static ProjectDto fromEntity(Project project) {
        if (project == null) {
            return null;
        }
        return new ProjectDto(
            project.getId(),
            project.getOrganization() != null ? project.getOrganization().getId() : null,
            project.getName(),
            project.getProjectKey(),
            project.getDescription(),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }
}
