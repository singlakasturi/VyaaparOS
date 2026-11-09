package com.vyapaaros.order.service;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
@Service
public class OrderScopeService {
 private final RetailerRepository retailers; private final RetailerAuthorizationService authorization;
 private final DistributorAuthorizationService distributorAuthorization;
 public OrderScopeService(RetailerRepository r,RetailerAuthorizationService a){this(r,a,null);}
 @Autowired
 public OrderScopeService(RetailerRepository r,RetailerAuthorizationService a,DistributorAuthorizationService d){retailers=r;authorization=a;distributorAuthorization=d;}
 public void require(UUID userId,UUID retailerId,Authentication auth){
  if(!retailers.existsById(retailerId)) throw new com.vyapaaros.common.exception.ResourceNotFoundException("Retailer not found.");
  if(has(auth,"ROLE_RETAILER")) {
   authorization.requireMembership(userId,retailerId);
   if(distributorAuthorization != null) distributorAuthorization.requireCurrentDistributorForRetailer(retailerId);
  } else if(has(auth,"ROLE_ORDER_DESK") && distributorAuthorization != null) {
   DistributorBusiness distributor = distributorAuthorization.requireCurrentDistributorForRetailer(retailerId);
   distributorAuthorization.requireOrderDeskAccess(userId, distributor.getId());
  } else throw new AccessDeniedException("Retailer order access denied.");
 }
 public void requireHistoricalOrderAccess(UUID userId,UUID retailerId,Authentication auth){
  if(!retailers.existsById(retailerId)) throw new com.vyapaaros.common.exception.ResourceNotFoundException("Retailer not found.");
  if(!has(auth,"ROLE_RETAILER")) throw new AccessDeniedException("Retailer order access denied.");
  authorization.requireMembership(userId,retailerId);
 }
 public DistributorBusiness currentDistributorForRetailer(UUID retailerId){
  if(distributorAuthorization == null) throw new AccessDeniedException("Distributor relationship is required.");
  return distributorAuthorization.requireCurrentDistributorForRetailer(retailerId);
 }
 public boolean has(Authentication a,String role){return a!=null&&a.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);}
 public UUID userId(Authentication a){if(a==null||!(a.getPrincipal() instanceof UUID))throw new AccessDeniedException("Authentication required.");return (UUID)a.getPrincipal();}
}
