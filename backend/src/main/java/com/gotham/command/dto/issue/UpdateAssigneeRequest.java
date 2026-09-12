package com.gotham.command.dto.issue;

import java.util.UUID;

public record UpdateAssigneeRequest(
    UUID assigneeId
) {}
