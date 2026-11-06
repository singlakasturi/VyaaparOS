package com.vyapaaros.order.dto;
import jakarta.validation.constraints.Positive;
public record CartQuantityRequest(@Positive int paidCases) {}
