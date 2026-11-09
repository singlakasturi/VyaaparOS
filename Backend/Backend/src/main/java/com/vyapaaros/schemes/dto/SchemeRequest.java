package com.vyapaaros.schemes.dto;

import com.vyapaaros.schemes.entity.SchemeType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SchemeRequest(@NotNull SchemeType schemeType, @NotBlank @Size(max=150) String name,
    @Size(max=500) String description, UUID productId, UUID categoryId, UUID retailerId,
    @Positive Integer buyQuantity, @Positive Integer freeQuantity, @Positive Integer minimumQuantity,
    @DecimalMin(value="0.01") @DecimalMax("100.00") BigDecimal discountPercentage,
    Instant startsAt, Instant endsAt) {}
