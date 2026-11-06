package com.vyapaaros.user.controller;

import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev")
public class DevelopmentProtectedController {

    private final UserRepository userRepository;

    public DevelopmentProtectedController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/protected-test")
    public ResponseEntity<?> protectedTest() {
        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new ProtectedTestResponse(
            user.getId(),
            user.getEmail(),
            authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList()
        ));
    }

    private record ProtectedTestResponse(
        UUID userId,
        String email,
        List<String> authorities
    ) {
    }
}
