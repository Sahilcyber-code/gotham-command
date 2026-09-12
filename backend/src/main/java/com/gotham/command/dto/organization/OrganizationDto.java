package com.gotham.command.dto.organization;

import java.time.Instant;
import java.util.UUID;

import com.gotham.command.entity.Organization;

public record OrganizationDto(
    UUID id,
    String name,
    String slug,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
    public static OrganizationDto fromEntity(Organization org) {
        if (org == null) {
            return null;
        }
        return new OrganizationDto(
            org.getId(),
            org.getName(),
            org.getSlug(),
            org.getDescription(),
            org.getCreatedAt(),
            org.getUpdatedAt()
        );
    }
}
