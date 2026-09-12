package com.gotham.command.dto.issue;

import com.gotham.command.entity.IssueStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
    @NotNull IssueStatus status
) {}
