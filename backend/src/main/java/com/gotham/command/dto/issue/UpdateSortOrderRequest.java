package com.gotham.command.dto.issue;

import jakarta.validation.constraints.NotNull;

public record UpdateSortOrderRequest(
    @NotNull Double sortOrder
) {}
