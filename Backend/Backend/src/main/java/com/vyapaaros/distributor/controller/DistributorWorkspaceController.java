package com.vyapaaros.distributor.controller;

import com.vyapaaros.distributor.dto.AddDistributorStaffRequest;
import com.vyapaaros.distributor.dto.DistributorBusinessResponse;
import com.vyapaaros.distributor.dto.DistributorStaffResponse;
import com.vyapaaros.distributor.dto.UpdateDistributorBusinessRequest;
import com.vyapaaros.distributor.service.DistributorWorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/distributor/me")
public class DistributorWorkspaceController {
    private final DistributorWorkspaceService workspace;

    public DistributorWorkspaceController(DistributorWorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping
    public DistributorBusinessResponse getBusiness(Authentication authentication) {
        return workspace.getBusiness(userId(authentication));
    }

    @PutMapping
    public DistributorBusinessResponse updateBusiness(
        @Valid @RequestBody UpdateDistributorBusinessRequest request,
        Authentication authentication
    ) {
        return workspace.updateBusiness(userId(authentication), request.businessName());
    }

    @GetMapping("/staff")
    public List<DistributorStaffResponse> listStaff(Authentication authentication) {
        return workspace.listStaff(userId(authentication));
    }

    @PostMapping("/staff")
    public ResponseEntity<DistributorStaffResponse> addStaff(
        @Valid @RequestBody AddDistributorStaffRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(workspace.addStaff(userId(authentication), request.email()));
    }

    @DeleteMapping("/staff/{staffUserId}")
    public ResponseEntity<Void> revokeStaff(
        @PathVariable UUID staffUserId,
        Authentication authentication
    ) {
        workspace.revokeStaff(userId(authentication), staffUserId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required.");
        }
        return userId;
    }
}
