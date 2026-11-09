package com.vyapaaros.notification.dto;

import com.vyapaaros.notification.entity.Notification;
import com.vyapaaros.notification.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String title, String message,
    String resourceType, UUID resourceId, Instant createdAt, Instant readAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
            n.getResourceType(), n.getResourceId(), n.getCreatedAt(), n.getReadAt());
    }
}
