package com.vyapaaros.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record AiOrderApplyItem(@NotNull UUID productId, @Positive int paidCases) {}
