package com.vyapaaros.distributor.controller;

import com.vyapaaros.common.exception.GlobalExceptionHandler;
import com.vyapaaros.distributor.dto.DistributorBusinessResponse;
import com.vyapaaros.distributor.dto.DistributorStaffResponse;
import com.vyapaaros.distributor.service.DistributorWorkspaceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DistributorWorkspaceControllerTest {
    private final DistributorWorkspaceService workspace = mock(DistributorWorkspaceService.class);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DistributorWorkspaceController(workspace))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void businessReadAndUpdateUseAuthenticatedUserAndRequestDto() throws Exception {
        when(workspace.getBusiness(ownerId)).thenReturn(business(true));
        when(workspace.updateBusiness(ownerId, "Updated business")).thenReturn(business(true));

        mvc.perform(get("/api/distributor/me").principal(auth(ownerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("North Distribution"))
            .andExpect(jsonPath("$.ownerName").value("Owner"))
            .andExpect(jsonPath("$.ownerEmail").doesNotExist())
            .andExpect(jsonPath("$.canManageStaff").value(true));
        mvc.perform(put("/api/distributor/me").principal(auth(ownerId))
                .contentType("application/json").content("{\"businessName\":\"Updated business\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000001"));
        verify(workspace).updateBusiness(ownerId, "Updated business");
    }

    @Test
    void ownerStaffRoutesReturnExpectedStatusesAndSafeDtos() throws Exception {
        when(workspace.listStaff(ownerId)).thenReturn(List.of(
            new DistributorStaffResponse(staffId, "staff@example.com", "Staff", Instant.parse("2026-01-01T00:00:00Z"))));
        when(workspace.addStaff(ownerId, "staff@example.com"))
            .thenReturn(new DistributorStaffResponse(staffId, "staff@example.com", "Staff", Instant.now()));

        mvc.perform(get("/api/distributor/me/staff").principal(auth(ownerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("staff@example.com"));
        mvc.perform(post("/api/distributor/me/staff").principal(auth(ownerId))
                .contentType("application/json").content("{\"email\":\"staff@example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(staffId.toString()));
        mvc.perform(delete("/api/distributor/me/staff/" + staffId).principal(auth(ownerId)))
            .andExpect(status().isNoContent());
        verify(workspace).revokeStaff(ownerId, staffId);
    }

    @Test
    void invalidBusinessNameAndStaffEmailAreRejected() throws Exception {
        mvc.perform(put("/api/distributor/me").principal(auth(ownerId))
                .contentType("application/json").content("{\"businessName\":\" \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/distributor/me/staff").principal(auth(ownerId))
                .contentType("application/json").content("{\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(workspace);
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private DistributorBusinessResponse business(boolean owner) {
        return new DistributorBusinessResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "North Distribution", "Owner",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), owner, owner);
    }
}
