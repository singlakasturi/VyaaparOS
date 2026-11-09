package com.vyapaaros.distributor.repository;
import com.vyapaaros.distributor.entity.DistributorRetailerInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface DistributorRetailerInvitationRepository extends JpaRepository<DistributorRetailerInvitation, UUID> {
    Optional<DistributorRetailerInvitation> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from DistributorRetailerInvitation i where i.tokenHash = :hash")
    Optional<DistributorRetailerInvitation> lockByTokenHash(String hash);
    List<DistributorRetailerInvitation> findByDistributorIdOrderByCreatedAtDesc(UUID distributorId);
    Optional<DistributorRetailerInvitation> findByIdAndDistributorId(UUID id, UUID distributorId);
}
