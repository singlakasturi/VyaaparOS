package com.vyapaaros.retailer.dto;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RetailerResponse(
    UUID id,
    String businessName,
    String ownerName,
    String contactEmail,
    String contactPhone,
    String address,
    String city,
    String state,
    String pincode,
    BigDecimal creditLimit,
    BigDecimal outstandingBalance,
    RetailerStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public static RetailerResponse from(Retailer retailer) {
        return new RetailerResponse(
            retailer.getId(),
            retailer.getBusinessName(),
            retailer.getOwnerName(),
            retailer.getContactEmail(),
            retailer.getContactPhone(),
            retailer.getAddress(),
            retailer.getCity(),
            retailer.getState(),
            retailer.getPincode(),
            retailer.getCreditLimit(),
            retailer.getOutstandingBalance(),
            retailer.getStatus(),
            retailer.getCreatedAt(),
            retailer.getUpdatedAt()
        );
    }
}
