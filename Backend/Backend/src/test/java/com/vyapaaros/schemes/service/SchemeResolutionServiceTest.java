package com.vyapaaros.schemes.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.schemes.entity.*;
import com.vyapaaros.schemes.repository.ProductSchemeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SchemeResolutionServiceTest {
    private final ProductSchemeRepository repository = mock(ProductSchemeRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository memberships = mock(DistributorMembershipRepository.class);
    private final RetailerDistributorRelationshipRepository relationships = mock(RetailerDistributorRelationshipRepository.class);
    private final RetailerAuthorizationService retailerAuthorization = mock(RetailerAuthorizationService.class);
    private final DistributorAuthorizationService authorization = new DistributorAuthorizationService(businesses,memberships,relationships,retailerAuthorization);
    private final UUID userId = UUID.randomUUID();
    private final UUID distributorId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private SchemeResolutionService service;

    @BeforeEach
    void setUp() {
        User creator=new User(); creator.setId(UUID.randomUUID());
        DistributorBusiness business=new DistributorBusiness(); business.setId(distributorId); business.setCreatedBy(creator);
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(any(),eq(RelationshipStatus.ACTIVE)))
            .thenAnswer(inv -> Optional.of(relation((UUID)inv.getArgument(0),business)));
        when(products.countByIdInAndDistributorId(anyCollection(),eq(distributorId))).thenReturn(1L);
        when(retailerAuthorization.requireMembership(userId,retailerId)).thenReturn(new com.vyapaaros.retailer.entity.RetailerMembership());
        when(memberships.existsByDistributorIdAndUserIdAndStatus(eq(distributorId),eq(userId),eq(DistributorMembershipStatus.ACTIVE))).thenReturn(true);
        service = new SchemeResolutionService(repository, products, authorization,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void buyGetFreeUsesOnlyCompletePaidBlocks() {
        ProductScheme scheme = buy(10, 1);
        when(repository.findApplicable(List.of(productId), retailerId, Instant.parse("2026-01-01T00:00:00Z")))
            .thenReturn(List.of(scheme));
        for (int quantity : new int[] {9, 10, 19, 20}) {
            var result = service.evaluate(productId, retailerId, quantity, price("8.10"), admin());
            assertEquals(quantity / 10, result.freeCases());
            assertEquals(BigDecimal.ZERO.setScale(2), result.discountAmount());
        }
    }

    @Test
    void slabDiscountUsesHalfUpAndFullPaidQuantity() {
        ProductScheme scheme = slab(5, "3.00");
        when(repository.findApplicable(any(), eq(retailerId), any())).thenReturn(List.of(scheme));
        var result = service.evaluate(productId, retailerId, 7, price("10.01"), admin());
        assertEquals(new BigDecimal("2.10"), result.discountAmount());
        assertEquals(new BigDecimal("67.97"), result.finalAmount());
        var rounded = service.evaluate(productId, retailerId, 5, price("10.01"), admin());
        assertEquals(new BigDecimal("1.50"), rounded.discountAmount());
        assertEquals(new BigDecimal("48.55"), rounded.finalAmount());
    }

    @Test
    void unavailablePriceDoesNotProducePayableAmount() {
        when(repository.findApplicable(any(), eq(retailerId), any())).thenReturn(List.of(buy(10, 1)));
        var result = service.evaluate(productId, retailerId, 10, ResolvedPrice.unavailable(), admin());
        assertFalse(result.priceAvailable());
        assertNull(result.finalAmount());
    }

    @Test
    void retailerSpecificSchemeWinsForSameType() {
        ProductScheme fallback = slab(5, "3.00");
        ProductScheme override = slab(5, "5.00");
        var retailer = new com.vyapaaros.retailer.entity.Retailer();
        fallback.setId(UUID.randomUUID()); override.setId(UUID.randomUUID());
        override.setRetailer(retailer);
        when(repository.findApplicable(any(), eq(retailerId), any())).thenReturn(List.of(fallback, override));
        var result = service.evaluate(productId, retailerId, 5, price("100.00"), authRetailer());
        assertEquals(new BigDecimal("5.00"), result.discountPercentage());
    }

    private ProductScheme buy(int buy, int free) {
        ProductScheme s = base(SchemeType.BUY_X_GET_Y_FREE);
        s.setBuyQuantity(buy); s.setFreeQuantity(free); return s;
    }
    private ProductScheme slab(int minimum, String percentage) {
        ProductScheme s = base(SchemeType.SLAB_DISCOUNT);
        s.setMinimumQuantity(minimum); s.setDiscountPercentage(new BigDecimal(percentage)); return s;
    }
    private ProductScheme base(SchemeType type) {
        ProductScheme s = new ProductScheme(); s.setId(UUID.randomUUID()); s.setProduct(productId());
        s.setSchemeType(type); s.setName("test"); s.setActive(true); return s;
    }
    private com.vyapaaros.catalog.entity.Product productId() {
        var p = new com.vyapaaros.catalog.entity.Product(); p.setId(productId); return p;
    }
    private ResolvedPrice price(String amount) {
        return new ResolvedPrice(true, new BigDecimal(amount), "INR", null, null);
    }
    private UsernamePasswordAuthenticationToken admin() {
        return authRetailer();
    }
    private UsernamePasswordAuthenticationToken authRetailer() { return new UsernamePasswordAuthenticationToken(userId,null,List.of(new SimpleGrantedAuthority("ROLE_RETAILER"))); }
    private RetailerDistributorRelationship relation(UUID retailerId, DistributorBusiness business) {
        var retailer=new com.vyapaaros.retailer.entity.Retailer(); retailer.setId(retailerId);
        var relation=new RetailerDistributorRelationship(); relation.setRetailer(retailer); relation.setDistributor(business);
        relation.setStatus(RelationshipStatus.ACTIVE); return relation;
    }
}
