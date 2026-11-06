package com.vyapaaros.order.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record CartItemRequest(@NotNull UUID retailerId,@NotNull UUID productId,@Positive int paidCases) {}
