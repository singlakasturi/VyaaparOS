package com.vyapaaros.pricing.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.pricing.entity.ProductPrice;
import com.vyapaaros.pricing.repository.ProductPriceRepository;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PriceResolutionServiceTest {
    private final ProductPriceRepository priceRepository = mock(ProductPriceRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository memberships = mock(DistributorMembershipRepository.class);
    private final RetailerDistributorRelationshipRepository relationships = mock(RetailerDistributorRelationshipRepository.class);
    private final RetailerAuthorizationService retailerAuthorization = mock(RetailerAuthorizationService.class);
    private final DistributorAuthorizationService authorization = new DistributorAuthorizationService(businesses,memberships,relationships,retailerAuthorization);
    private final UUID userId=UUID.randomUUID(), retailerId=UUID.randomUUID(), distributorId=UUID.randomUUID(), productId=UUID.randomUUID();
    private PriceResolutionService service;

    @BeforeEach void setUp() {
        reset(priceRepository, products, businesses, memberships, relationships, retailerAuthorization);
        User creator=new User(); creator.setId(UUID.randomUUID()); DistributorBusiness business=new DistributorBusiness(); business.setId(distributorId); business.setCreatedBy(creator);
        Retailer retailer=new Retailer(); retailer.setId(retailerId); RetailerDistributorRelationship relation=new RetailerDistributorRelationship(); relation.setRetailer(retailer); relation.setDistributor(business); relation.setStatus(RelationshipStatus.ACTIVE);
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId,RelationshipStatus.ACTIVE)).thenReturn(Optional.of(relation));
        when(retailerAuthorization.requireMembership(userId,retailerId)).thenReturn(new com.vyapaaros.retailer.entity.RetailerMembership());
        when(products.countByIdInAndDistributorId(anyCollection(),eq(distributorId))).thenReturn(1L);
        service=new PriceResolutionService(priceRepository,products,authorization);
    }

    @Test void resolvesDefaultAndRetailerOverrideOnlyForActiveLinkedProducts() {
        ProductPrice fallback=price(productId,"10.00",null), override=price(productId,"9.50",true);
        when(priceRepository.findActiveDefaults(List.of(productId))).thenReturn(List.of(fallback));
        when(priceRepository.findActiveOverrides(List.of(productId),retailerId)).thenReturn(List.of(override));
        var result=service.resolve(auth("ROLE_RETAILER"),retailerId,List.of(productId)).get(productId);
        assertTrue(result.priceAvailable()); assertEquals(new BigDecimal("9.50"),result.pricePerCase());
        assertEquals("RETAILER_OVERRIDE",result.priceSource().name()); assertEquals(Boolean.TRUE,result.gstIncluded());
        verify(retailerAuthorization).requireMembership(userId,retailerId);
    }

    @Test void rejectsGlobalDeskWithoutBusinessMembershipAndCrossBusinessProductIds() {
        assertThrows(AccessDeniedException.class,()->service.resolve(auth("ROLE_ORDER_DESK"),null,distributorId,List.of(productId)));
        when(products.countByIdInAndDistributorId(anyCollection(),eq(distributorId))).thenReturn(0L);
        assertThrows(AccessDeniedException.class,()->service.resolve(auth("ROLE_RETAILER"),retailerId,List.of(productId)));
        verifyNoInteractions(priceRepository);
    }

    @Test void endedRelationshipBlocksRetailerPriceLookup() {
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId,RelationshipStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,()->service.resolve(auth("ROLE_RETAILER"),retailerId,List.of(productId)));
        verifyNoInteractions(priceRepository);
    }

    private ProductPrice price(UUID id,String amount,Boolean gst) { Product product=new Product(); product.setId(id); ProductPrice p=new ProductPrice(); p.setProduct(product); p.setPricePerCase(new BigDecimal(amount)); p.setCurrency("INR"); p.setGstIncluded(gst); p.setActive(true); return p; }
    private UsernamePasswordAuthenticationToken auth(String role) { return new UsernamePasswordAuthenticationToken(userId,null,List.of(new SimpleGrantedAuthority(role))); }
}
