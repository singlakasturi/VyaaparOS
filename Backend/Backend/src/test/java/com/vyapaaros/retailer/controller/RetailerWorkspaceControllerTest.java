package com.vyapaaros.retailer.controller;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetailerWorkspaceControllerTest {

    @Test
    void listsOnlyMembershipsForAuthenticatedUser() throws Exception {
        RetailerMembershipRepository repository = mock(RetailerMembershipRepository.class);
        UUID userId = UUID.randomUUID();
        Retailer retailer = new Retailer();
        retailer.setId(UUID.randomUUID());
        retailer.setBusinessName("Corner Market");
        retailer.setStatus(RetailerStatus.ACTIVE);
        RetailerMembership membership = new RetailerMembership();
        membership.setRetailer(retailer);
        when(repository.findByUserId(userId)).thenReturn(List.of(membership));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new RetailerWorkspaceController(repository)
        ).build();

        mvc.perform(get("/api/retailers/mine").principal(
                new UsernamePasswordAuthenticationToken(userId, "token", AuthorityUtils.NO_AUTHORITIES)
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].businessName").value("Corner Market"));
        verify(repository).findByUserId(userId);
    }
}
