package com.gotham.command.dto.comment;

import java.time.Instant;
import java.util.UUID;

import com.gotham.command.dto.user.UserDto;
import com.gotham.command.entity.Comment;

public record CommentDto(
    UUID id,
    UUID issueId,
    UserDto author,
    String body,
    Instant createdAt,
    Instant updatedAt
) {
    public static CommentDto fromEntity(Comment comment) {
        if (comment == null) {
            return null;
        }
        return new CommentDto(
            comment.getId(),
            comment.getIssue() != null ? comment.getIssue().getId() : null,
            comment.getAuthor() != null ? UserDto.fromEntity(comment.getAuthor()) : null,
            comment.getBody(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
