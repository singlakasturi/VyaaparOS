package com.vyapaaros.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AiOrderApplyRequest(
    @NotNull UUID retailerId,
    @NotEmpty List<@Valid AiOrderApplyItem> items
) {}
