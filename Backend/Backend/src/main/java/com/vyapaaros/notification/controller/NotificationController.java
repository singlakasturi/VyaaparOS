package com.vyapaaros.notification.controller;

import com.vyapaaros.notification.dto.NotificationResponse;
import com.vyapaaros.notification.service.NotificationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @GetMapping public Page<NotificationResponse> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){return service.list(userId(auth),page,size);}
    @GetMapping("/unread-count") public long unreadCount(Authentication auth){return service.unreadCount(userId(auth));}
    @PatchMapping("/{id}/read") public ResponseEntity<Void> markRead(@PathVariable UUID id,Authentication auth){service.markRead(userId(auth),id);return ResponseEntity.noContent().build();}
    @PatchMapping("/read-all") public ResponseEntity<Void> markAllRead(Authentication auth){service.markAllRead(userId(auth));return ResponseEntity.noContent().build();}
    private UUID userId(Authentication auth){if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof UUID id))throw new AccessDeniedException("Authentication required.");return id;}
}
