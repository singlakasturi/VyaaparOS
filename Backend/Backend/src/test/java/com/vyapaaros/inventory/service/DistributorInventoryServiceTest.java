package com.vyapaaros.inventory.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.entity.ProductCategory;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.inventory.entity.*;
import com.vyapaaros.inventory.repository.*;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DistributorInventoryServiceTest {
    private final DistributorAuthorizationService authorization=mock(DistributorAuthorizationService.class);
    private final DistributorBusinessRepository businesses=mock(DistributorBusinessRepository.class);
    private final ProductRepository products=mock(ProductRepository.class);
    private final InventoryRepository inventory=mock(InventoryRepository.class);
    private final InventoryAdjustmentRepository adjustments=mock(InventoryAdjustmentRepository.class);
    private final InventoryMovementRepository movementRepository=mock(InventoryMovementRepository.class);
    private final InventoryMovementService movementService=mock(InventoryMovementService.class);
    private final UserRepository users=mock(UserRepository.class);
    private final DistributorInventoryService service=new DistributorInventoryService(authorization,businesses,products,inventory,adjustments,users,movementRepository,movementService);
    private final UUID userId=UUID.randomUUID(),businessId=UUID.randomUUID(),productId=UUID.randomUUID();

    @Test void authorizedStockAdjustmentUpdatesAvailableAndRecordsActorReasonAndResult(){
        Product product=product();Inventory row=inventory(product,12);User actor=new User();actor.setId(userId);actor.setFullName("Order Desk User");
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(product));
        when(inventory.lockByProductIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(row));when(businesses.getReferenceById(businessId)).thenReturn(product.getDistributor());when(users.getReferenceById(userId)).thenReturn(actor);
        when(inventory.save(any())).thenAnswer(inv->inv.getArgument(0));when(adjustments.save(any())).thenAnswer(inv->inv.getArgument(0));when(adjustments.findTop100ByInventoryIdAndDistributorIdOrderByCreatedAtDescIdDesc(any(),eq(businessId))).thenReturn(List.of());
        var result=service.adjust(userId,productId,5,"Received delivery");
        assertEquals(17,result.availableCases());assertEquals(17,result.onHandCases());assertEquals(17,row.getAvailableCases());assertEquals(17,row.getOnHandCases());
        org.mockito.ArgumentCaptor<InventoryAdjustment> captor=org.mockito.ArgumentCaptor.forClass(InventoryAdjustment.class);verify(adjustments).save(captor.capture());
        assertEquals(5,captor.getValue().getQuantityDelta());assertEquals(17,captor.getValue().getQuantityAfter());assertEquals("Received delivery",captor.getValue().getReason());assertEquals(userId,captor.getValue().getActor().getId());assertNotNull(captor.getValue().getCreatedAt());
        verify(inventory).lockByProductIdAndDistributorId(productId,businessId);
        verify(movementService).manualAdjustment(any(InventoryAdjustment.class));
    }

    @Test void stockCannotBeAdjustedBelowZeroOrByZero(){
        Product product=product();Inventory row=inventory(product,2);when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(product));when(inventory.lockByProductIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(row));
        assertThrows(IllegalArgumentException.class,()->service.adjust(userId,productId,-3,"Count correction"));
        assertThrows(IllegalArgumentException.class,()->service.adjust(userId,productId,0,"No change"));
        verify(inventory,never()).save(any());verify(adjustments,never()).save(any());
    }

    @Test void adjustmentCannotReduceAvailableBelowExistingReservations(){
        Product product=product();Inventory row=inventory(product,2);row.setReservedCases(4);row.setOnHandCases(6);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(product));when(inventory.lockByProductIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(row));
        assertThrows(IllegalArgumentException.class,()->service.adjust(userId,productId,-3,"Counted below reserved"));
        assertEquals(2,row.getAvailableCases());assertEquals(6,row.getOnHandCases());verify(inventory,never()).save(any());
    }

    @Test void lowStockThresholdChangesOnlyThresholdAndIsDistributorScoped(){
        Product product=product();Inventory row=inventory(product,12);when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(product));when(inventory.lockByProductIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(row));when(inventory.save(any())).thenAnswer(inv->inv.getArgument(0));when(adjustments.findTop100ByInventoryIdAndDistributorIdOrderByCreatedAtDescIdDesc(any(),eq(businessId))).thenReturn(List.of());
        var updated=service.updateThreshold(userId,productId,15);
        assertEquals(15,updated.lowStockThreshold());assertTrue(updated.lowStock());assertEquals(12,updated.availableCases());assertEquals(12,updated.onHandCases());assertEquals(0,updated.reservedCases());
        assertThrows(IllegalArgumentException.class,()->service.updateThreshold(userId,productId,-1));
    }

    @Test void inventorySearchPassesDistributorSearchAndLowStockToPagedQuery(){
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));
        var page=org.springframework.data.domain.PageRequest.of(2,10);
        when(inventory.searchForDistributor(businessId,"tea",true,page)).thenReturn(org.springframework.data.domain.Page.empty(page));
        var result=service.list(userId," Tea ",true,page);
        assertEquals(0,result.getTotalElements());verify(inventory).searchForDistributor(businessId,"tea",true,page);
    }

    @Test void movementHistoryScopesFiltersAndStablePagingToAuthorizedBusiness(){
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));
        var pageable=org.springframework.data.domain.PageRequest.of(1,10,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"),org.springframework.data.domain.Sort.Order.desc("id")));
        when(movementRepository.searchForDistributor(eq(businessId),eq(productId),eq(InventoryMovementType.DELIVERY_CONSUMPTION),any(),any(),eq("ord-9"),isNull(),any())).thenReturn(org.springframework.data.domain.Page.empty(pageable));
        var page=service.movementHistory(userId,productId,InventoryMovementType.DELIVERY_CONSUMPTION,Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-02-01T00:00:00Z")," ORD-9 ",1,10);
        assertEquals(0,page.getTotalElements());verify(movementRepository).searchForDistributor(eq(businessId),eq(productId),eq(InventoryMovementType.DELIVERY_CONSUMPTION),any(),any(),eq("ord-9"),isNull(),eq(pageable));
    }

    @Test void invalidMovementDateRangesAndPaginationAreRejected(){
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));
        Instant time=Instant.parse("2026-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,()->service.movementHistory(userId,null,null,time,time,null,0,20));
        assertThrows(IllegalArgumentException.class,()->service.movementHistory(userId,null,null,null,null,null,-1,20));
        assertThrows(IllegalArgumentException.class,()->service.movementHistory(userId,null,null,null,null,null,0,101));
        verifyNoInteractions(movementRepository);
    }

    @Test void reconciliationComparesMovementDerivedBalancesFromBaselineAndReportsPartialCoverage(){
        Product product=product();Inventory row=inventory(product,12);row.setOnHandCases(15);row.setReservedCases(3);row.setAvailableCases(12);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(product));when(inventory.findByProductIdAndDistributorId(productId,businessId)).thenReturn(Optional.of(row));
        InventoryMovement baseline=new InventoryMovement();baseline.setOnHandAfter(10);baseline.setReservedAfter(2);baseline.setAvailableAfter(8);baseline.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(movementRepository.findFirstByInventoryIdAndMovementTypeOrderByCreatedAtAscIdAsc(row.getId(),InventoryMovementType.OPENING_BALANCE)).thenReturn(Optional.of(baseline));
        when(movementRepository.sumOnHandDeltas(row.getId(),InventoryMovementType.OPENING_BALANCE,baseline.getCreatedAt())).thenReturn(5L);
        when(movementRepository.sumReservedDeltas(row.getId(),InventoryMovementType.OPENING_BALANCE,baseline.getCreatedAt())).thenReturn(1L);
        var result=service.reconcile(userId,productId);
        assertTrue(result.ledgerCompleteSinceBaseline());assertEquals(15L,result.ledgerOnHand());assertEquals(3L,result.ledgerReserved());assertEquals(12L,result.ledgerAvailable());assertEquals(false,result.discrepancyDetected());
        when(movementRepository.findFirstByInventoryIdAndMovementTypeOrderByCreatedAtAscIdAsc(row.getId(),InventoryMovementType.OPENING_BALANCE)).thenReturn(Optional.empty());
        var partial=service.reconcile(userId,productId);assertFalse(partial.ledgerCompleteSinceBaseline());assertNull(partial.ledgerOnHand());assertNull(partial.discrepancyDetected());
    }

    @Test void productFromAnotherDistributorCannotBeAdjusted(){
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(productId,businessId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.adjust(userId,productId,1,"Count correction"));verify(inventory,never()).lockByProductIdAndDistributorId(any(),any());
    }

    @Test void unauthorizedCallerCannotReadInventory(){when(authorization.activeOrderDeskBusinessIds(userId)).thenThrow(new AccessDeniedException("denied"));assertThrows(AccessDeniedException.class,()->service.list(userId,null,false,org.springframework.data.domain.PageRequest.of(0,20)));verifyNoInteractions(inventory,products);}

    @Test void adjustmentRepositoryLocksInventoryRowAndMutationIsTransactional() throws Exception {
        var method=InventoryRepository.class.getMethod("lockByProductIdAndDistributorId",UUID.class,UUID.class);Lock lock=method.getAnnotation(Lock.class);
        assertNotNull(lock);assertEquals(LockModeType.PESSIMISTIC_WRITE,lock.value());
        assertNotNull(DistributorInventoryService.class.getMethod("adjust",UUID.class,UUID.class,Integer.class,String.class).getAnnotation(Transactional.class));
        assertTrue(InventoryRepository.class.getMethod("reserve",UUID.class,UUID.class,int.class).getAnnotation(org.springframework.data.jpa.repository.Query.class).value().contains("reservedCases=i.reservedCases+:quantity"));
        assertTrue(InventoryRepository.class.getMethod("consumeReserved",UUID.class,UUID.class,int.class).getAnnotation(org.springframework.data.jpa.repository.Query.class).value().contains("reservedCases >= :quantity"));
    }

    private Product product(){DistributorBusiness b=new DistributorBusiness();b.setId(businessId);ProductCategory c=new ProductCategory();c.setId(UUID.randomUUID());c.setName("Tea");c.setSlug("tea");c.setActive(true);Product p=new Product();p.setId(productId);p.setDistributor(b);p.setCategory(c);p.setSku("TEA-1");p.setName("Tea");p.setActive(true);return p;}
    private Inventory inventory(Product p,int count){Inventory i=new Inventory();i.setId(UUID.randomUUID());i.setProduct(p);i.setDistributor(p.getDistributor());i.setAvailableCases(count);i.setOnHandCases(count);i.setReservedCases(0);i.setLowStockThreshold(0);i.setUpdatedAt(Instant.now());return i;}
}
