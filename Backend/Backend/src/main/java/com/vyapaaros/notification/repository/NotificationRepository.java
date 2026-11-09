package com.vyapaaros.notification.repository;

import com.vyapaaros.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
    java.util.Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);
    @Modifying
    @Query(value = "INSERT INTO notifications (id, recipient_id, notification_type, title, message, resource_type, resource_id, dedupe_key, created_at) " +
        "VALUES (:id, :recipient, :type, :title, :message, :resourceType, :resourceId, :dedupeKey, :createdAt) " +
        "ON CONFLICT (dedupe_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("recipient") UUID recipient, @Param("type") String type,
        @Param("title") String title, @Param("message") String message, @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId, @Param("dedupeKey") String dedupeKey, @Param("createdAt") Instant createdAt);
    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.recipient.id = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
