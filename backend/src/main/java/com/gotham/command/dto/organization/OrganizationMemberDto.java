package com.gotham.command.dto.organization;

import java.time.Instant;
import java.util.UUID;

import com.gotham.command.dto.user.UserDto;
import com.gotham.command.entity.OrganizationMember;
import com.gotham.command.entity.OrganizationRole;

public record OrganizationMemberDto(
    UUID id,
    UUID organizationId,
    UserDto user,
    OrganizationRole role,
    Instant joinedAt
) {
    public static OrganizationMemberDto fromEntity(OrganizationMember member) {
        if (member == null) {
            return null;
        }
        return new OrganizationMemberDto(
            member.getId(),
            member.getOrganization() != null ? member.getOrganization().getId() : null,
            member.getUser() != null ? UserDto.fromEntity(member.getUser()) : null,
            member.getRole(),
            member.getJoinedAt()
        );
    }
}
