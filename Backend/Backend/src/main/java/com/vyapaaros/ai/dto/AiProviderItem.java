package com.vyapaaros.ai.dto;
public record AiProviderItem(String originalText, String productDescription, int paidCases, String unit,
    boolean unitAmbiguous, double confidence) {
    public AiProviderItem(String originalText, String productDescription, int paidCases,
        String unit, double confidence) {
        this(originalText, productDescription, paidCases, unit, false, confidence);
    }
}
