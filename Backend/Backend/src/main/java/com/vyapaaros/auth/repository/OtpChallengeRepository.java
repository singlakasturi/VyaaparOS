package com.vyapaaros.auth.repository;

import com.vyapaaros.auth.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    List<OtpChallenge> findByEmailOrderByCreatedAtDesc(String email);

    Optional<OtpChallenge> findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(
        String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT challenge
        FROM OtpChallenge challenge
        WHERE challenge.email = :email
          AND challenge.consumedAt IS NULL
        ORDER BY challenge.createdAt DESC
        """)
    Optional<OtpChallenge> findLatestActiveByEmailForUpdate(
        @Param("email") String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findFirstByEmailOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtGreaterThanEqual(String email, Instant createdAt);

    List<OtpChallenge> findByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
        String email,
        Instant currentTime
    );
}
