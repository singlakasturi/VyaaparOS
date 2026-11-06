package com.vyapaaros.security.jwt;

import com.vyapaaros.security.entity.Role;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT authentication filter that:
 * 1. Parses and validates the JWT signature and expiry.
 * 2. Loads the current user record from the database on every request.
 * 3. Rejects requests where the user is disabled, deleted, or has had roles changed.
 *
 * Loading from DB on every request is intentional: correctness and immediate
 * revocation take priority over caching for this application.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            UUID userId = UUID.fromString(claims.getSubject());

            // Load current user state from DB to detect disable/delete/role changes
            Optional<User> maybeUser = userRepository.findWithRolesById(userId);

            if (maybeUser.isEmpty()) {
                // User deleted after JWT was issued
                writeUnauthorized(response, "Unauthorized");
                return;
            }

            User user = maybeUser.get();

            if (!ACTIVE_STATUS.equals(user.getStatus())) {
                // User disabled or suspended after JWT was issued
                writeUnauthorized(response, "Unauthorized");
                return;
            }

            // Build authorities from the CURRENT database roles, not the JWT claims.
            // This ensures role changes are reflected immediately without token reissue.
            Collection<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(Role::getName)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, authorities)
            );
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Unauthorized");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}"
        );
    }
}
