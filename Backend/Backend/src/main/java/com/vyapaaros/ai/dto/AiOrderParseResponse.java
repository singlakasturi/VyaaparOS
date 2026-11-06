package com.vyapaaros.ai.dto;
import java.util.List;
import java.util.UUID;
public record AiOrderParseResponse(
    UUID parseRequestId,
    String originalText,
    AiParseStatus status,
    List<AiParsedItemResponse> items,
    List<String> missingInformation
) {}
