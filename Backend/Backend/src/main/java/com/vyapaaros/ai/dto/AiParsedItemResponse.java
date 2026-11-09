package com.vyapaaros.ai.dto;
import java.util.List;
public record AiParsedItemResponse(
    String originalText,
    ProductResolutionStatus resolutionStatus,
    AiProductResponse product,
    List<AiCandidateResponse> candidates,
    Integer paidCases,
    String unit,
    boolean unitAmbiguous,
    double confidence,
    String explanation
) {
    public AiParsedItemResponse(String originalText, ProductResolutionStatus resolutionStatus,
        AiProductResponse product, List<AiCandidateResponse> candidates, Integer paidCases,
        String unit, double confidence, String explanation) {
        this(originalText, resolutionStatus, product, candidates, paidCases, unit, false,
            confidence, explanation);
    }
}
