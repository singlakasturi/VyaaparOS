package com.vyapaaros.distributor.dto;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import java.time.Instant;
import java.util.UUID;

public record DistributorBusinessResponse(
    UUID id,
    String businessName,
    String ownerName,
    Instant createdAt,
    Instant updatedAt,
    boolean owner,
    boolean canManageStaff
) {
    public static DistributorBusinessResponse from(DistributorBusiness business, boolean owner) {
        return new DistributorBusinessResponse(
            business.getId(), business.getBusinessName(),
            business.getCreatedBy() == null ? null : business.getCreatedBy().getFullName(),
            business.getCreatedAt(), business.getUpdatedAt(), owner, owner
        );
    }
}
