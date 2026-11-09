package com.vyapaaros.distributor.repository;
import com.vyapaaros.distributor.entity.RetailerRelationshipRequest;
import com.vyapaaros.distributor.entity.RetailerRelationshipRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface RetailerRelationshipRequestRepository extends JpaRepository<RetailerRelationshipRequest, UUID> {
    List<RetailerRelationshipRequest> findByDistributorIdAndStatusOrderByCreatedAtAsc(UUID id, RequestStatus status);
    Optional<RetailerRelationshipRequest> findByIdAndDistributorId(UUID id, UUID distributorId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RetailerRelationshipRequest r where r.id = :id and r.distributor.id = :distributorId")
    Optional<RetailerRelationshipRequest> lockByIdAndDistributorId(UUID id, UUID distributorId);
    List<RetailerRelationshipRequest> findByRetailerIdOrderByCreatedAtDesc(UUID retailerId);
    Optional<RetailerRelationshipRequest> findFirstByRetailerIdAndStatus(UUID retailerId, RequestStatus status);
}
