package com.vyapaaros.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AiOrderParseRequest(
    @NotNull UUID retailerId,
    @NotBlank @Size(max = 4000) String text
) {}
