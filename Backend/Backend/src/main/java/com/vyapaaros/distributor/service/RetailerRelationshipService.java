package com.vyapaaros.distributor.service;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.entity.RetailerRelationshipRequest.RequestStatus;
import com.vyapaaros.distributor.entity.RetailerRelationshipRequest.RequestType;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.retailer.entity.*;
import com.vyapaaros.retailer.repository.*;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import com.vyapaaros.notification.service.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RetailerRelationshipService {
    private final DistributorBusinessRepository businesses;
    private final RetailerRelationshipRequestRepository requests;
    private final DistributorRetailerInvitationRepository invitations;
    private final RetailerDistributorRelationshipRepository relationships;
    private final RetailerRepository retailers;
    private final RetailerMembershipRepository retailerMemberships;
    private final UserRepository users;
    private final DistributorAuthorizationService distributorAuth;
    private final RetailerAuthorizationService retailerAuth;
    private final NotificationService notifications;

    public RetailerRelationshipService(DistributorBusinessRepository businesses, RetailerRelationshipRequestRepository requests,
      DistributorRetailerInvitationRepository invitations, RetailerDistributorRelationshipRepository relationships,
      RetailerRepository retailers, RetailerMembershipRepository retailerMemberships, UserRepository users,
      DistributorAuthorizationService distributorAuth, RetailerAuthorizationService retailerAuth) {
        this(businesses,requests,invitations,relationships,retailers,retailerMemberships,users,distributorAuth,retailerAuth,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RetailerRelationshipService(DistributorBusinessRepository businesses, RetailerRelationshipRequestRepository requests,
      DistributorRetailerInvitationRepository invitations, RetailerDistributorRelationshipRepository relationships,
      RetailerRepository retailers, RetailerMembershipRepository retailerMemberships, UserRepository users,
      DistributorAuthorizationService distributorAuth, RetailerAuthorizationService retailerAuth, NotificationService notifications) {
        this.businesses=businesses; this.requests=requests; this.invitations=invitations; this.relationships=relationships;
        this.retailers=retailers; this.retailerMemberships=retailerMemberships; this.users=users;
        this.distributorAuth=distributorAuth; this.retailerAuth=retailerAuth; this.notifications=notifications;
    }

    @Transactional(readOnly=true)
    public List<DistributorSummary> discover(String query) {
        if (query == null || query.trim().length() < 2) return List.of();
        return businesses.findTop20ByBusinessNameContainingIgnoreCaseOrderByBusinessNameAsc(query.trim()).stream()
          .map(b -> new DistributorSummary(b.getId(), b.getBusinessName())).toList();
    }

    @Transactional(readOnly=true)
    public List<RequestView> myRequests(UUID userId, UUID retailerId) {
        retailerAuth.requireOwner(userId, retailerId);
        return requests.findByRetailerIdOrderByCreatedAtDesc(retailerId).stream().map(RequestView::from).toList();
    }

    @Transactional(readOnly=true)
    public RelationshipView currentDistributor(UUID userId, UUID retailerId) {
        retailerAuth.requireOwner(userId, retailerId);
        return relationships.findFirstByRetailerIdOrderByStartedAtDesc(retailerId)
          .map(r -> new RelationshipView(r.getDistributor().getId(), r.getDistributor().getBusinessName(), r.getStatus().name(), r.getStartedAt(), r.getEndedAt())).orElse(null);
    }

    @Transactional
    public RequestView request(UUID userId, UUID retailerId, UUID distributorId, RequestType type) {
        Retailer ownerChecked = ownerRetailer(userId, retailerId);
        DistributorBusiness target = businesses.findById(distributorId).orElseThrow(() -> new ResourceNotFoundException("Distributor not found."));
        var active = relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE);
        if (active.isPresent() && type == RequestType.JOIN) throw new ResourceConflictException("Request a switch to change distributors.");
        if (active.isEmpty() && type == RequestType.SWITCH) throw new ResourceConflictException("There is no active distributor to switch from.");
        if (active.isPresent() && active.get().getDistributor().getId().equals(distributorId)) throw new ResourceConflictException("This is already the active distributor.");
        if (requests.findFirstByRetailerIdAndStatus(retailerId, RequestStatus.PENDING).isPresent()) throw new ResourceConflictException("A relationship request is already pending.");
        RetailerRelationshipRequest req = new RetailerRelationshipRequest(); req.setRetailer(ownerChecked); req.setDistributor(target);
        req.setRequestedBy(users.getReferenceById(userId)); req.setRequestType(type); req.setStatus(RequestStatus.PENDING); req.setCreatedAt(Instant.now());
        RetailerRelationshipRequest saved=requests.save(req);
        if(notifications!=null) notifications.relationshipRequestReceived(saved.getId(),target.getId(),userId);
        return RequestView.from(saved);
    }

    @Transactional
    public void cancelRequest(UUID userId, UUID requestId) {
        RetailerRelationshipRequest req = requests.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("Request not found."));
        retailerAuth.requireOwner(userId, req.getRetailer().getId());
        if (req.getStatus() != RequestStatus.PENDING) throw new ResourceConflictException("Only pending requests can be cancelled.");
        decide(req, RequestStatus.CANCELLED, userId);
    }

    @Transactional(readOnly=true)
    public List<RequestView> pendingForDistributor(UUID userId) {
        UUID id = currentBusinessId(userId);
        return requests.findByDistributorIdAndStatusOrderByCreatedAtAsc(id, RequestStatus.PENDING).stream().map(RequestView::from).toList();
    }

    @Transactional
    public RequestView approve(UUID userId, UUID requestId) {
        UUID businessId=currentBusinessId(userId);
        RetailerRelationshipRequest req=requests.lockByIdAndDistributorId(requestId,businessId).orElseThrow(() -> new ResourceNotFoundException("Request not found."));
        if(req.getStatus()!=RequestStatus.PENDING) throw new ResourceConflictException("Request is no longer pending.");
        Retailer locked=retailers.findById(req.getRetailer().getId()).orElseThrow(() -> new ResourceNotFoundException("Retailer not found."));
        var active=relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(locked.getId(),RelationshipStatus.ACTIVE);
        if(req.getRequestType()==RequestType.JOIN && active.isPresent()) throw new ResourceConflictException("Retailer already has an active distributor.");
        if(req.getRequestType()==RequestType.SWITCH && active.isEmpty()) throw new ResourceConflictException("The existing relationship has ended.");
        if(active.isPresent()) { active.get().setStatus(RelationshipStatus.ENDED); active.get().setEndedAt(Instant.now()); relationships.saveAndFlush(active.get()); }
        activate(locked,req.getDistributor(),userId);
        decide(req,RequestStatus.APPROVED,userId);
        if(notifications!=null) notifications.relationshipRequestDecision(req.getId(),req.getRequestedBy().getId(),true);
        return RequestView.from(req);
    }

    @Transactional
    public RequestView reject(UUID userId, UUID requestId) {
        UUID businessId=currentBusinessId(userId);
        RetailerRelationshipRequest req=requests.lockByIdAndDistributorId(requestId,businessId).orElseThrow(() -> new ResourceNotFoundException("Request not found."));
        if(req.getStatus()!=RequestStatus.PENDING) throw new ResourceConflictException("Request is no longer pending.");
        decide(req,RequestStatus.REJECTED,userId); if(notifications!=null) notifications.relationshipRequestDecision(req.getId(),req.getRequestedBy().getId(),false); return RequestView.from(req);
    }

    @Transactional(readOnly=true)
    public List<RetailerView> activeRetailers(UUID userId) {
        UUID businessId=currentBusinessId(userId);
        return relationships.findByDistributorIdAndStatusOrderByStartedAtDesc(businessId,RelationshipStatus.ACTIVE).stream()
          .map(r -> RetailerView.from(r.getRetailer(),r.getStartedAt())).toList();
    }

    @Transactional
    public void removeRetailer(UUID userId, UUID retailerId) {
        UUID businessId=currentBusinessId(userId);
        retailers.findById(retailerId).orElseThrow(() -> new ResourceNotFoundException("Retailer not found."));
        var relationship=relationships.findFirstByRetailerIdAndDistributorIdAndStatus(retailerId,businessId,RelationshipStatus.ACTIVE)
          .orElseThrow(() -> new ResourceNotFoundException("Active retailer relationship not found."));
        relationship.setStatus(RelationshipStatus.ENDED); relationship.setEndedAt(Instant.now()); relationships.save(relationship);
    }

    @Transactional
    public InvitationCreated createInvitation(UUID userId,String email) {
        UUID businessId=currentBusinessId(userId); String normalized=email.trim().toLowerCase(Locale.ROOT);
        if(!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now=Instant.now(); DistributorRetailerInvitation invite=new DistributorRetailerInvitation();
        invite.setDistributor(businesses.getReferenceById(businessId)); invite.setInvitedEmail(normalized); invite.setTokenHash(hash(token));
        invite.setCreatedBy(users.getReferenceById(userId)); invite.setCreatedAt(now); invite.setExpiresAt(now.plus(7,ChronoUnit.DAYS)); invitations.save(invite);
        return new InvitationCreated(invite.getId(),normalized,invite.getExpiresAt(),token);
    }

    @Transactional(readOnly=true)
    public List<InvitationView> invitations(UUID userId) {
        UUID businessId=currentBusinessId(userId);
        return invitations.findByDistributorIdOrderByCreatedAtDesc(businessId).stream().map(InvitationView::from).toList();
    }

    @Transactional
    public void revokeInvitation(UUID userId,UUID invitationId) {
        UUID businessId=currentBusinessId(userId);
        DistributorRetailerInvitation invite=invitations.findByIdAndDistributorId(invitationId,businessId).orElseThrow(() -> new ResourceNotFoundException("Invitation not found."));
        if(invite.getRevokedAt()!=null||invite.getAcceptedAt()!=null) throw new ResourceConflictException("Invitation is already closed.");
        invite.setRevokedAt(Instant.now());
    }

    @Transactional(readOnly=true)
    public InvitationPreview previewInvitation(String token) {
        DistributorRetailerInvitation invite=validInvitation(token);
        return new InvitationPreview(invite.getDistributor().getBusinessName(),invite.getInvitedEmail(),invite.getExpiresAt());
    }

    @Transactional
    public void acceptInvitation(UUID userId,String token,UUID retailerId) {
        User user=users.findWithRolesById(userId).filter(u -> "ACTIVE".equals(u.getStatus())).orElseThrow(() -> new AccessDeniedException("Account is inactive."));
        DistributorRetailerInvitation invite=validInvitation(token);
        if(!invite.getInvitedEmail().equalsIgnoreCase(user.getEmail())) throw new AccessDeniedException("This invitation belongs to another account.");
        Retailer retailer=ownerRetailer(userId,retailerId);
        if(!retailer.getContactEmail().equalsIgnoreCase(user.getEmail())) throw new AccessDeniedException("Retailer account email does not match this invitation.");
        var active=relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId,RelationshipStatus.ACTIVE);
        if(active.isPresent()&&active.get().getDistributor().getId().equals(invite.getDistributor().getId())) throw new ResourceConflictException("Retailer is already linked to this distributor.");
        if(active.isPresent()){active.get().setStatus(RelationshipStatus.ENDED);active.get().setEndedAt(Instant.now());relationships.saveAndFlush(active.get());}
        activate(retailer,invite.getDistributor(),userId);
        invite.setAcceptedBy(user);invite.setAcceptedRetailer(retailer);invite.setAcceptedAt(Instant.now());
    }

    private DistributorRetailerInvitation validInvitation(String token) {
        DistributorRetailerInvitation invite=invitations.lockByTokenHash(hash(token)).orElseThrow(() -> new ResourceNotFoundException("Invitation is invalid or expired."));
        if(invite.getRevokedAt()!=null||invite.getAcceptedAt()!=null||!invite.getExpiresAt().isAfter(Instant.now())) throw new ResourceNotFoundException("Invitation is invalid or expired.");
        return invite;
    }
    private Retailer ownerRetailer(UUID userId,UUID retailerId) { retailerAuth.requireOwner(userId,retailerId); return retailers.findById(retailerId).orElseThrow(() -> new ResourceNotFoundException("Retailer not found.")); }
    private UUID currentBusinessId(UUID userId) { Set<UUID> ids=distributorAuth.activeOrderDeskBusinessIds(userId); if(ids.size()!=1) throw new AccessDeniedException("Select a single authorized distributor workspace."); UUID id=ids.iterator().next(); distributorAuth.requireOrderDeskAccess(userId,id); return id; }
    private void activate(Retailer retailer,DistributorBusiness business,UUID userId) { RetailerDistributorRelationship r=new RetailerDistributorRelationship();r.setRetailer(retailer);r.setDistributor(business);r.setStatus(RelationshipStatus.ACTIVE);r.setStartedBy(users.getReferenceById(userId));r.setStartedAt(Instant.now());relationships.saveAndFlush(r); }
    private void decide(RetailerRelationshipRequest req,RequestStatus status,UUID userId) { req.setStatus(status);req.setDecidedAt(Instant.now());req.setDecidedBy(users.getReferenceById(userId));requests.save(req); }
    private static String hash(String token) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); } catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);} }

    public record DistributorSummary(UUID id,String businessName){}
    public record RelationshipView(UUID id,String businessName,String status,Instant startedAt,Instant endedAt){}
    public record RequestView(UUID id,UUID retailerId,String retailerName,String retailerEmail,UUID distributorId,String distributorName,String type,String status,Instant createdAt) { static RequestView from(RetailerRelationshipRequest r){Retailer t=r.getRetailer();return new RequestView(r.getId(),t.getId(),t.getBusinessName(),t.getContactEmail(),r.getDistributor().getId(),r.getDistributor().getBusinessName(),r.getRequestType().name(),r.getStatus().name(),r.getCreatedAt());} }
    public record RetailerView(UUID id,String businessName,String ownerName,String email,String phone,String city,Instant linkedAt){static RetailerView from(Retailer r,Instant at){return new RetailerView(r.getId(),r.getBusinessName(),r.getOwnerName(),r.getContactEmail(),r.getContactPhone(),r.getCity(),at);}}
    public record InvitationCreated(UUID id,String email,Instant expiresAt,String token){}
    public record InvitationView(UUID id,String email,Instant createdAt,Instant expiresAt,Instant revokedAt,Instant acceptedAt){static InvitationView from(DistributorRetailerInvitation i){return new InvitationView(i.getId(),i.getInvitedEmail(),i.getCreatedAt(),i.getExpiresAt(),i.getRevokedAt(),i.getAcceptedAt());}}
    public record InvitationPreview(String distributorName,String invitedEmail,Instant expiresAt){}
}
