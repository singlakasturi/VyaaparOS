package com.vyapaaros.retailer.dto;

import com.vyapaaros.catalog.dto.ProductResponse;
import java.time.Instant;
import java.util.UUID;

public record RetailerFavoriteResponse(UUID productId, Instant savedAt, boolean catalogActive,
    boolean orderableNow, ProductResponse product) { }
