package com.vyapaaros.notification.entity;

import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_id", nullable = false) private User recipient;
    @Enumerated(EnumType.STRING) @Column(name = "notification_type", nullable = false, length = 40) private NotificationType type;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 400) private String message;
    @Column(name = "resource_type", nullable = false, length = 32) private String resourceType;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    @Column(name = "dedupe_key", nullable = false, unique = true, length = 160) private String dedupeKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "read_at") private Instant readAt;
    protected Notification() {}
    public UUID getId(){return id;} public User getRecipient(){return recipient;} public NotificationType getType(){return type;}
    public String getTitle(){return title;} public String getMessage(){return message;} public String getResourceType(){return resourceType;}
    public UUID getResourceId(){return resourceId;} public Instant getCreatedAt(){return createdAt;} public Instant getReadAt(){return readAt;}
    public void setReadAt(Instant value){readAt=value;}
}
