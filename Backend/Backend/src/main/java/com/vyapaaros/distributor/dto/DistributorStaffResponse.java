package com.vyapaaros.distributor.dto;

import com.vyapaaros.distributor.entity.DistributorMembership;
import java.time.Instant;
import java.util.UUID;

public record DistributorStaffResponse(UUID userId, String email, String fullName, Instant grantedAt) {
    public static DistributorStaffResponse from(DistributorMembership membership) {
        return new DistributorStaffResponse(
            membership.getUser().getId(), membership.getUser().getEmail(),
            membership.getUser().getFullName(), membership.getGrantedAt()
        );
    }
}
