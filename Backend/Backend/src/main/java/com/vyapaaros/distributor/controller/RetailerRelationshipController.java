package com.vyapaaros.distributor.controller;

import com.vyapaaros.distributor.entity.RetailerRelationshipRequest.RequestType;
import com.vyapaaros.distributor.service.RetailerRelationshipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class RetailerRelationshipController {
    private final RetailerRelationshipService service;
    public RetailerRelationshipController(RetailerRelationshipService service){this.service=service;}

    @GetMapping("/api/retailer-relationships/distributors")
    public List<RetailerRelationshipService.DistributorSummary> discover(@RequestParam(defaultValue="") String q){return service.discover(q);}
    @GetMapping("/api/retailer-relationships/requests")
    public List<RetailerRelationshipService.RequestView> myRequests(@RequestParam UUID retailerId,Authentication a){return service.myRequests(userId(a),retailerId);}
    @GetMapping("/api/retailer-relationships/current")
    public RetailerRelationshipService.RelationshipView current(@RequestParam UUID retailerId,Authentication a){return service.currentDistributor(userId(a),retailerId);}
    @PostMapping("/api/retailer-relationships/requests")
    public ResponseEntity<RetailerRelationshipService.RequestView> request(@Valid @RequestBody CreateRequest body,Authentication a){return ResponseEntity.status(HttpStatus.CREATED).body(service.request(userId(a),body.retailerId(),body.distributorId(),body.type()));}
    @DeleteMapping("/api/retailer-relationships/requests/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id,Authentication a){service.cancelRequest(userId(a),id);return ResponseEntity.noContent().build();}
    @GetMapping("/api/retailer-relationships/invitations/{token}")
    public RetailerRelationshipService.InvitationPreview preview(@PathVariable String token){return service.previewInvitation(token);}
    @PostMapping("/api/retailer-relationships/invitations/{token}/accept")
    public ResponseEntity<Void> accept(@PathVariable String token,@Valid @RequestBody AcceptInvitation body,Authentication a){service.acceptInvitation(userId(a),token,body.retailerId());return ResponseEntity.noContent().build();}

    @GetMapping("/api/distributor/me/retailer-requests")
    public List<RetailerRelationshipService.RequestView> pending(Authentication a){return service.pendingForDistributor(userId(a));}
    @PostMapping("/api/distributor/me/retailer-requests/{id}/approve")
    public RetailerRelationshipService.RequestView approve(@PathVariable UUID id,Authentication a){return service.approve(userId(a),id);}
    @PostMapping("/api/distributor/me/retailer-requests/{id}/reject")
    public RetailerRelationshipService.RequestView reject(@PathVariable UUID id,Authentication a){return service.reject(userId(a),id);}
    @GetMapping("/api/distributor/me/retailers")
    public List<RetailerRelationshipService.RetailerView> active(Authentication a){return service.activeRetailers(userId(a));}
    @DeleteMapping("/api/distributor/me/retailers/{retailerId}")
    public ResponseEntity<Void> remove(@PathVariable UUID retailerId,Authentication a){service.removeRetailer(userId(a),retailerId);return ResponseEntity.noContent().build();}
    @GetMapping("/api/distributor/me/retailer-invitations")
    public List<RetailerRelationshipService.InvitationView> invitations(Authentication a){return service.invitations(userId(a));}
    @PostMapping("/api/distributor/me/retailer-invitations")
    public ResponseEntity<RetailerRelationshipService.InvitationCreated> invite(@Valid @RequestBody CreateInvitation body,Authentication a){return ResponseEntity.status(HttpStatus.CREATED).body(service.createInvitation(userId(a),body.email()));}
    @DeleteMapping("/api/distributor/me/retailer-invitations/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,Authentication a){service.revokeInvitation(userId(a),id);return ResponseEntity.noContent().build();}

    private UUID userId(Authentication a){if(a==null||!a.isAuthenticated()||!(a.getPrincipal() instanceof UUID id))throw new AuthenticationCredentialsNotFoundException("Authentication required.");return id;}
    public record CreateRequest(@NotNull UUID retailerId,@NotNull UUID distributorId,@NotNull RequestType type){}
    public record AcceptInvitation(@NotNull UUID retailerId){}
    public record CreateInvitation(@NotNull @Email String email){}
}
