package com.gotham.command.dto.issue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.gotham.command.entity.Issue;
import com.gotham.command.entity.IssuePriority;
import com.gotham.command.entity.IssueStatus;
import com.gotham.command.entity.IssueType;

public record IssueDto(
    UUID id,
    UUID projectId,
    UUID parentIssueId,
    String issueKey,
    String title,
    String description,
    IssueType issueType,
    IssueStatus status,
    IssuePriority priority,
    UUID reporterId,
    UUID assigneeId,
    List<String> labels,
    Double sortOrder,
    LocalDate dueDate,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    List<SubtaskDto> subtasks
) {
    public static IssueDto fromEntity(Issue issue) {
        if (issue == null) {
            return null;
        }
        List<SubtaskDto> subtaskDtos;
        try {
            subtaskDtos = (issue.getSubtasks() != null && org.hibernate.Hibernate.isInitialized(issue.getSubtasks()))
                ? issue.getSubtasks().stream().map(SubtaskDto::fromEntity).toList()
                : List.of();
        } catch (Exception e) {
            subtaskDtos = List.of();
        }

        return new IssueDto(
            issue.getId(),
            issue.getProject() != null ? issue.getProject().getId() : null,
            issue.getParentIssue() != null ? issue.getParentIssue().getId() : null,
            issue.getIssueKey(),
            issue.getTitle(),
            issue.getDescription(),
            issue.getIssueType(),
            issue.getStatus(),
            issue.getPriority(),
            issue.getReporter() != null ? issue.getReporter().getId() : null,
            issue.getAssignee() != null ? issue.getAssignee().getId() : null,
            issue.getLabels().stream().toList(),
            issue.getSortOrder(),
            issue.getDueDate(),
            issue.getCreatedAt(),
            issue.getUpdatedAt(),
            issue.getResolvedAt(),
            subtaskDtos
        );
    }
}
