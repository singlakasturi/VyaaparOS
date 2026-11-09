package com.vyapaaros.distributor.service;

import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.retailer.entity.*;
import com.vyapaaros.retailer.repository.*;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetailerRelationshipServiceTest {
    private final DistributorBusinessRepository businesses=mock(DistributorBusinessRepository.class);
    private final RetailerRelationshipRequestRepository requests=mock(RetailerRelationshipRequestRepository.class);
    private final DistributorRetailerInvitationRepository invitations=mock(DistributorRetailerInvitationRepository.class);
    private final RetailerDistributorRelationshipRepository relationships=mock(RetailerDistributorRelationshipRepository.class);
    private final RetailerRepository retailers=mock(RetailerRepository.class);
    private final RetailerMembershipRepository retailerMemberships=mock(RetailerMembershipRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final DistributorMembershipRepository distributorMemberships=mock(DistributorMembershipRepository.class);
    private final RetailerRelationshipService service=new RetailerRelationshipService(businesses,requests,invitations,relationships,retailers,retailerMemberships,users,
      new DistributorAuthorizationService(businesses,distributorMemberships,relationships),new RetailerAuthorizationService(retailerMemberships));
    private final UUID userId=UUID.randomUUID(), retailerId=UUID.randomUUID(), distributorId=UUID.randomUUID();

    @Test void discoveryReturnsOnlyPublicBusinessIdentifierAndName(){
        DistributorBusiness b=new DistributorBusiness();b.setId(distributorId);b.setBusinessName("North Distribution");b.setCreatedAt(Instant.now());b.setUpdatedAt(Instant.now());
        when(businesses.findTop20ByBusinessNameContainingIgnoreCaseOrderByBusinessNameAsc("North")).thenReturn(List.of(b));
        var result=service.discover(" North ");
        assertEquals(List.of(new RetailerRelationshipService.DistributorSummary(distributorId,"North Distribution")),result);
    }

    @Test void retailerWithoutPersistedOwnerMembershipCannotCreateJoinRequest(){
        when(retailerMemberships.findByUserIdAndRetailerId(userId,retailerId)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,()->service.request(userId,retailerId,distributorId,RetailerRelationshipRequest.RequestType.JOIN));
        verifyNoInteractions(requests);
    }

    @Test void authorizedRetailerCanCreateJoinRequestWhenNoCurrentRelationshipExists(){
        Retailer retailer=new Retailer();retailer.setId(retailerId);retailer.setBusinessName("Corner Store");
        RetailerMembership membership=new RetailerMembership();membership.setRole(RetailerMembershipRole.RETAILER_OWNER);membership.setRetailer(retailer);
        User user=new User();user.setId(userId);
        DistributorBusiness target=new DistributorBusiness();target.setId(distributorId);target.setBusinessName("North Distribution");
        when(retailerMemberships.findByUserIdAndRetailerId(userId,retailerId)).thenReturn(Optional.of(membership));
        when(retailers.findById(retailerId)).thenReturn(Optional.of(retailer));
        when(businesses.findById(distributorId)).thenReturn(Optional.of(target));
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId,RetailerDistributorRelationship.RelationshipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(requests.findFirstByRetailerIdAndStatus(retailerId,RetailerRelationshipRequest.RequestStatus.PENDING)).thenReturn(Optional.empty());
        when(users.getReferenceById(userId)).thenReturn(user);
        when(requests.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        var result=service.request(userId,retailerId,distributorId,RetailerRelationshipRequest.RequestType.JOIN);
        assertEquals("PENDING",result.status());assertEquals("JOIN",result.type());assertEquals("North Distribution",result.distributorName());
    }

    @Test void expiredOrRevokedInvitationCannotBePreviewedOrAccepted(){
        DistributorRetailerInvitation invite=new DistributorRetailerInvitation();invite.setTokenHash("x");invite.setExpiresAt(Instant.now().minusSeconds(2));
        when(invitations.lockByTokenHash(any())).thenReturn(Optional.of(invite));
        assertThrows(RuntimeException.class,()->service.previewInvitation("unguessable-token"));
        invite.setExpiresAt(Instant.now().plusSeconds(100));invite.setRevokedAt(Instant.now());
        assertThrows(RuntimeException.class,()->service.previewInvitation("unguessable-token"));
        verify(relationships,never()).save(any());
    }

    @Test void switchApprovalEndsOldRelationshipAndCreatesNewRelationshipInSameServiceTransaction(){
        User owner=new User();owner.setId(userId);
        DistributorBusiness destination=new DistributorBusiness();destination.setId(distributorId);destination.setBusinessName("New Distributor");destination.setCreatedBy(owner);
        UUID oldBusinessId=UUID.randomUUID();DistributorBusiness previous=new DistributorBusiness();previous.setId(oldBusinessId);previous.setCreatedBy(owner);
        Retailer retailer=new Retailer();retailer.setId(retailerId);retailer.setBusinessName("Corner Store");
        RetailerRelationshipRequest request=new RetailerRelationshipRequest();request.setRetailer(retailer);request.setDistributor(destination);request.setRequestType(RetailerRelationshipRequest.RequestType.SWITCH);request.setStatus(RetailerRelationshipRequest.RequestStatus.PENDING);
        RetailerDistributorRelationship old=new RetailerDistributorRelationship();old.setRetailer(retailer);old.setDistributor(previous);old.setStatus(RetailerDistributorRelationship.RelationshipStatus.ACTIVE);old.setStartedAt(Instant.now());
        when(businesses.findAllByCreatedById(userId)).thenReturn(List.of(destination));when(businesses.findById(distributorId)).thenReturn(Optional.of(destination));
        when(distributorMemberships.findByUserIdAndStatus(userId,DistributorMembershipStatus.ACTIVE)).thenReturn(List.of());
        when(requests.lockByIdAndDistributorId(UUID.fromString("00000000-0000-0000-0000-000000000123"),distributorId)).thenReturn(Optional.of(request));
        when(retailers.findById(retailerId)).thenReturn(Optional.of(retailer));
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId,RetailerDistributorRelationship.RelationshipStatus.ACTIVE)).thenReturn(Optional.of(old));
        when(users.getReferenceById(userId)).thenReturn(owner);
        when(relationships.saveAndFlush(any())).thenAnswer(invocation->invocation.getArgument(0));
        var result=service.approve(userId,UUID.fromString("00000000-0000-0000-0000-000000000123"));
        assertEquals("APPROVED",result.status());assertEquals(RetailerDistributorRelationship.RelationshipStatus.ENDED,old.getStatus());assertNotNull(old.getEndedAt());
        org.mockito.ArgumentCaptor<RetailerDistributorRelationship> captor=org.mockito.ArgumentCaptor.forClass(RetailerDistributorRelationship.class);
        verify(relationships,times(2)).saveAndFlush(captor.capture());
        assertEquals(RetailerDistributorRelationship.RelationshipStatus.ACTIVE,captor.getAllValues().get(1).getStatus());
        assertEquals(distributorId,captor.getAllValues().get(1).getDistributor().getId());
        assertThrows(com.vyapaaros.common.exception.ResourceConflictException.class,()->service.approve(userId,UUID.fromString("00000000-0000-0000-0000-000000000123")));
        verify(relationships,times(2)).saveAndFlush(any());
    }

    @Test void requestFromAnotherBusinessCannotBeApproved(){
        User owner=new User();owner.setId(userId);DistributorBusiness home=new DistributorBusiness();home.setId(distributorId);home.setCreatedBy(owner);
        UUID foreignBusinessId=UUID.randomUUID();UUID requestId=UUID.randomUUID();
        when(businesses.findAllByCreatedById(userId)).thenReturn(List.of(home));when(businesses.findById(distributorId)).thenReturn(Optional.of(home));
        when(distributorMemberships.findByUserIdAndStatus(userId,DistributorMembershipStatus.ACTIVE)).thenReturn(List.of());
        when(requests.lockByIdAndDistributorId(requestId,distributorId)).thenReturn(Optional.empty());
        assertThrows(com.vyapaaros.common.exception.ResourceNotFoundException.class,()->service.approve(userId,requestId));
        verify(relationships,never()).save(any());verify(relationships,never()).saveAndFlush(any());
        assertNotEquals(distributorId,foreignBusinessId);
    }

    @Test void requestingJoinWhileAlreadyLinkedIsRejected(){
        Retailer retailer=new Retailer();retailer.setId(retailerId);RetailerMembership membership=new RetailerMembership();membership.setRole(RetailerMembershipRole.RETAILER_OWNER);
        DistributorBusiness target=new DistributorBusiness();target.setId(distributorId);RetailerDistributorRelationship current=new RetailerDistributorRelationship();current.setDistributor(new DistributorBusiness());current.getDistributor().setId(UUID.randomUUID());
        when(retailerMemberships.findByUserIdAndRetailerId(userId,retailerId)).thenReturn(Optional.of(membership));when(retailers.findById(retailerId)).thenReturn(Optional.of(retailer));
        when(businesses.findById(distributorId)).thenReturn(Optional.of(target));when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId,RetailerDistributorRelationship.RelationshipStatus.ACTIVE)).thenReturn(Optional.of(current));
        assertThrows(com.vyapaaros.common.exception.ResourceConflictException.class,()->service.request(userId,retailerId,distributorId,RetailerRelationshipRequest.RequestType.JOIN));
        verify(requests,never()).save(any());
    }

    @Test void rejectingSwitchDoesNotEndExistingRelationship(){
        User owner=new User();owner.setId(userId);DistributorBusiness home=new DistributorBusiness();home.setId(distributorId);home.setCreatedBy(owner);
        Retailer retailer=new Retailer();retailer.setId(retailerId);retailer.setBusinessName("Corner Store");
        RetailerRelationshipRequest request=new RetailerRelationshipRequest();request.setRetailer(retailer);request.setDistributor(home);request.setRequestType(RetailerRelationshipRequest.RequestType.SWITCH);request.setCreatedAt(Instant.now());request.setStatus(RetailerRelationshipRequest.RequestStatus.PENDING);
        when(businesses.findAllByCreatedById(userId)).thenReturn(List.of(home));when(businesses.findById(distributorId)).thenReturn(Optional.of(home));
        when(distributorMemberships.findByUserIdAndStatus(userId,DistributorMembershipStatus.ACTIVE)).thenReturn(List.of());
        when(requests.lockByIdAndDistributorId(any(),eq(distributorId))).thenReturn(Optional.of(request));when(users.getReferenceById(userId)).thenReturn(owner);
        assertEquals("REJECTED",service.reject(userId,UUID.randomUUID()).status());verify(relationships,never()).save(any());verify(relationships,never()).saveAndFlush(any());
    }

    @Test void invitationPreviewDoesNotCreateRelationshipAndWrongEmailCannotAccept(){
        String token="bearer-invite-token";String hash;
        try{hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(Exception e){throw new AssertionError(e);}
        User invitee=new User();invitee.setId(userId);invitee.setEmail("another@example.com");invitee.setStatus("ACTIVE");
        DistributorBusiness business=new DistributorBusiness();business.setId(distributorId);business.setBusinessName("North Distribution");
        DistributorRetailerInvitation invite=new DistributorRetailerInvitation();invite.setDistributor(business);invite.setInvitedEmail("target@example.com");invite.setExpiresAt(Instant.now().plusSeconds(1000));
        when(invitations.lockByTokenHash(hash)).thenReturn(Optional.of(invite));when(users.findWithRolesById(userId)).thenReturn(Optional.of(invitee));
        assertEquals("North Distribution",service.previewInvitation(token).distributorName());
        assertThrows(AccessDeniedException.class,()->service.acceptInvitation(userId,token,retailerId));
        verify(relationships,never()).save(any());verify(relationships,never()).saveAndFlush(any());
    }

    @Test void invitationCreationStoresOnlyTokenHashAndReturnsRawTokenToCreator(){
        User owner=new User();owner.setId(userId);DistributorBusiness home=new DistributorBusiness();home.setId(distributorId);home.setCreatedBy(owner);
        when(businesses.findAllByCreatedById(userId)).thenReturn(List.of(home));when(businesses.findById(distributorId)).thenReturn(Optional.of(home));
        when(distributorMemberships.findByUserIdAndStatus(userId,DistributorMembershipStatus.ACTIVE)).thenReturn(List.of());when(users.getReferenceById(userId)).thenReturn(owner);
        org.mockito.ArgumentCaptor<DistributorRetailerInvitation> captor=org.mockito.ArgumentCaptor.forClass(DistributorRetailerInvitation.class);
        when(invitations.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        var result=service.createInvitation(userId,"retailer@example.com");verify(invitations).save(captor.capture());
        assertEquals("retailer@example.com",result.email());assertEquals(43,result.token().length());assertEquals(64,captor.getValue().getTokenHash().length());assertNotEquals(result.token(),captor.getValue().getTokenHash());
    }

    @Test void activeOrderDeskStaffCanReviewOnlyTheirPersistedBusinessRequests(){
        UUID staffId=UUID.randomUUID();User owner=new User();owner.setId(UUID.randomUUID());User staff=new User();staff.setId(staffId);
        DistributorBusiness home=new DistributorBusiness();home.setId(distributorId);home.setCreatedBy(owner);
        DistributorMembership membership=new DistributorMembership();membership.setDistributor(home);membership.setUser(staff);membership.setStatus(DistributorMembershipStatus.ACTIVE);
        Retailer retailer=new Retailer();retailer.setId(retailerId);retailer.setBusinessName("Corner Store");retailer.setContactEmail("store@example.com");
        RetailerRelationshipRequest request=new RetailerRelationshipRequest();request.setRetailer(retailer);request.setDistributor(home);request.setRequestType(RetailerRelationshipRequest.RequestType.JOIN);request.setStatus(RetailerRelationshipRequest.RequestStatus.PENDING);request.setCreatedAt(Instant.now());
        when(distributorMemberships.findByUserIdAndStatus(staffId,DistributorMembershipStatus.ACTIVE)).thenReturn(List.of(membership));
        when(businesses.findAllByCreatedById(staffId)).thenReturn(List.of());when(businesses.findById(distributorId)).thenReturn(Optional.of(home));
        when(distributorMemberships.existsByDistributorIdAndUserIdAndStatus(distributorId,staffId,DistributorMembershipStatus.ACTIVE)).thenReturn(true);
        when(requests.findByDistributorIdAndStatusOrderByCreatedAtAsc(distributorId,RetailerRelationshipRequest.RequestStatus.PENDING)).thenReturn(List.of(request));
        var result=service.pendingForDistributor(staffId);
        assertEquals(1,result.size());assertEquals("Corner Store",result.get(0).retailerName());
        verify(requests).findByDistributorIdAndStatusOrderByCreatedAtAsc(distributorId,RetailerRelationshipRequest.RequestStatus.PENDING);
    }

    @Test void removalEndsOnlyTheActiveRelationshipAndRetainsItsHistoryRow(){
        User owner=new User();owner.setId(userId);DistributorBusiness home=new DistributorBusiness();home.setId(distributorId);home.setCreatedBy(owner);
        Retailer retailer=new Retailer();retailer.setId(retailerId);RetailerDistributorRelationship active=new RetailerDistributorRelationship();active.setRetailer(retailer);active.setDistributor(home);active.setStatus(RetailerDistributorRelationship.RelationshipStatus.ACTIVE);active.setStartedAt(Instant.now());
        when(businesses.findAllByCreatedById(userId)).thenReturn(List.of(home));when(businesses.findById(distributorId)).thenReturn(Optional.of(home));
        when(distributorMemberships.findByUserIdAndStatus(userId,DistributorMembershipStatus.ACTIVE)).thenReturn(List.of());when(retailers.findById(retailerId)).thenReturn(Optional.of(retailer));
        when(relationships.findFirstByRetailerIdAndDistributorIdAndStatus(retailerId,distributorId,RetailerDistributorRelationship.RelationshipStatus.ACTIVE)).thenReturn(Optional.of(active));
        service.removeRetailer(userId,retailerId);
        assertEquals(RetailerDistributorRelationship.RelationshipStatus.ENDED,active.getStatus());assertNotNull(active.getEndedAt());verify(relationships).save(active);
        verify(relationships,never()).delete(any());
    }
}
