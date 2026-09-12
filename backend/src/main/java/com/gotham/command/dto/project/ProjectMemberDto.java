package com.gotham.command.dto.project;

import java.time.Instant;
import java.util.UUID;

import com.gotham.command.dto.user.UserDto;
import com.gotham.command.entity.ProjectMember;
import com.gotham.command.entity.ProjectRole;

public record ProjectMemberDto(
    UUID id,
    UUID projectId,
    UserDto user,
    ProjectRole role,
    Instant joinedAt
) {
    public static ProjectMemberDto fromEntity(ProjectMember member) {
        if (member == null) {
            return null;
        }
        return new ProjectMemberDto(
            member.getId(),
            member.getProject() != null ? member.getProject().getId() : null,
            member.getUser() != null ? UserDto.fromEntity(member.getUser()) : null,
            member.getRole(),
            member.getJoinedAt()
        );
    }
}
