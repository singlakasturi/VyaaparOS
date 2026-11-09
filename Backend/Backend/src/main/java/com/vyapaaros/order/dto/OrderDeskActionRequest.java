package com.vyapaaros.order.dto;

import jakarta.validation.constraints.Size;

public record OrderDeskActionRequest(@Size(max = 1000) String reason) {}
