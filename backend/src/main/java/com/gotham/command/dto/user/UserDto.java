package com.gotham.command.dto.user;

import java.time.Instant;
import java.util.UUID;

import com.gotham.command.entity.AuthProvider;
import com.gotham.command.entity.User;

public record UserDto(
    UUID id,
    String email,
    String fullName,
    String displayName,
    String username,
    String avatarUrl,
    AuthProvider provider,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static UserDto fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getDisplayName(),
            user.getUsername(),
            user.getAvatarUrl(),
            user.getProvider(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
