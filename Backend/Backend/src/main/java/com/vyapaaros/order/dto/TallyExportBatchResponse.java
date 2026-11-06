package com.vyapaaros.order.dto;

import com.vyapaaros.order.entity.TallyExportBatch;
import java.time.Instant;
import java.util.*;

public record TallyExportBatchResponse(UUID id, String filename, UUID createdBy, Instant createdAt,
    int orderCount, List<String> orderNumbers) {
    public static TallyExportBatchResponse from(TallyExportBatch b, List<String> numbers) {
        return new TallyExportBatchResponse(b.getId(), b.getFilename(), b.getCreatedBy().getId(),
            b.getCreatedAt(), b.getOrderCount(), numbers);
    }
}
