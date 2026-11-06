package com.vyapaaros.ai.dto;
import java.util.List;
public record AiParsedItemResponse(
    String originalText,
    ProductResolutionStatus resolutionStatus,
    AiProductResponse product,
    List<AiCandidateResponse> candidates,
    Integer paidCases,
    String unit,
    double confidence,
    String explanation
) {}
