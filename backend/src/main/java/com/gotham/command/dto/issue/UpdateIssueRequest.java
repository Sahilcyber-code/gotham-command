package com.gotham.command.dto.issue;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import com.gotham.command.entity.IssuePriority;
import com.gotham.command.entity.IssueStatus;
import com.gotham.command.entity.IssueType;

import jakarta.validation.constraints.Size;

public record UpdateIssueRequest(
    @Size(max = 255) String title,
    String description,
    IssueType issueType,
    IssueStatus status,
    IssuePriority priority,
    UUID assigneeId,
    List<String> labels,
    Double sortOrder,
    LocalDate dueDate
) {}
