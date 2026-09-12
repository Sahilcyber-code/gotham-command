package com.gotham.command.dto.issue;

import java.time.Instant;
import java.util.UUID;

import com.gotham.command.entity.Issue;
import com.gotham.command.entity.IssuePriority;
import com.gotham.command.entity.IssueStatus;
import com.gotham.command.entity.IssueType;

public record SubtaskDto(
    UUID id,
    UUID parentIssueId,
    String issueKey,
    String title,
    IssueType issueType,
    IssueStatus status,
    IssuePriority priority,
    UUID assigneeId,
    Instant createdAt
) {
    public static SubtaskDto fromEntity(Issue issue) {
        if (issue == null) {
            return null;
        }
        return new SubtaskDto(
            issue.getId(),
            issue.getParentIssue() != null ? issue.getParentIssue().getId() : null,
            issue.getIssueKey(),
            issue.getTitle(),
            issue.getIssueType(),
            issue.getStatus(),
            issue.getPriority(),
            issue.getAssignee() != null ? issue.getAssignee().getId() : null,
            issue.getCreatedAt()
        );
    }
}
