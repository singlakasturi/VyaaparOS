package com.vyapaaros.distributor.service;

import com.vyapaaros.catalog.dto.ProductUpsertRequest;
import com.vyapaaros.catalog.entity.*;
import com.vyapaaros.catalog.repository.*;
import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.inventory.entity.Inventory;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.inventory.service.InventoryMovementService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DistributorCatalogManagementServiceTest {
    private final DistributorAuthorizationService authorization=mock(DistributorAuthorizationService.class);
    private final DistributorBusinessRepository businesses=mock(DistributorBusinessRepository.class);
    private final ProductCategoryRepository categories=mock(ProductCategoryRepository.class);
    private final ProductRepository products=mock(ProductRepository.class);
    private final InventoryRepository inventory=mock(InventoryRepository.class);
    private final InventoryMovementService movementService=mock(InventoryMovementService.class);
    private final DistributorCatalogManagementService service=new DistributorCatalogManagementService(authorization,businesses,categories,products,inventory,movementService);
    private final UUID userId=UUID.randomUUID(),businessId=UUID.randomUUID(),categoryId=UUID.randomUUID();

    @Test void activeOwnerOrStaffCanCreateProductAndInitialInventoryWithinTheirBusiness(){
        DistributorBusiness business=business(businessId);ProductCategory category=category(categoryId,businessId);Product saved=new Product();saved.setId(UUID.randomUUID());
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(businesses.getReferenceById(businessId)).thenReturn(business);
        when(categories.findByIdAndDistributorIdAndActiveTrue(categoryId,businessId)).thenReturn(Optional.of(category));when(products.existsByDistributorIdAndSkuIgnoreCase(businessId,"SKU-1")).thenReturn(false);
        when(products.saveAndFlush(any())).thenAnswer(inv->{Product candidate=inv.getArgument(0);candidate.setId(saved.getId());return candidate;});when(inventory.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        var result=service.createProduct(userId,request("SKU-1","Tea",categoryId));
        assertEquals(saved.getId(),result.id());assertEquals("SKU-1",result.sku());assertEquals(categoryId,result.category().id());
        assertEquals(0,result.availableCases());
        org.mockito.ArgumentCaptor<Inventory> stock=org.mockito.ArgumentCaptor.forClass(Inventory.class);verify(inventory).saveAndFlush(stock.capture());
        assertEquals(businessId,stock.getValue().getDistributor().getId());assertEquals(0,stock.getValue().getAvailableCases());
        verify(movementService).openingBalance(stock.getValue(),"Initial product stock baseline.");
        verify(authorization).requireOrderDeskAccess(userId,businessId);
    }

    @Test void sameSkuCanBeCreatedByDifferentAuthorizedDistributors(){
        UUID otherUser=UUID.randomUUID(),otherBusinessId=UUID.randomUUID();
        DistributorBusiness first=business(businessId),second=business(otherBusinessId);ProductCategory firstCategory=category(categoryId,businessId),secondCategory=category(UUID.randomUUID(),otherBusinessId);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(authorization.activeOrderDeskBusinessIds(otherUser)).thenReturn(Set.of(otherBusinessId));
        when(businesses.getReferenceById(businessId)).thenReturn(first);when(businesses.getReferenceById(otherBusinessId)).thenReturn(second);
        when(categories.findByIdAndDistributorIdAndActiveTrue(categoryId,businessId)).thenReturn(Optional.of(firstCategory));when(categories.findByIdAndDistributorIdAndActiveTrue(secondCategory.getId(),otherBusinessId)).thenReturn(Optional.of(secondCategory));
        when(products.existsByDistributorIdAndSkuIgnoreCase(any(),eq("SHARED"))).thenReturn(false);when(products.saveAndFlush(any())).thenAnswer(inv->{Product p=inv.getArgument(0);p.setId(UUID.randomUUID());return p;});when(inventory.save(any())).thenAnswer(inv->inv.getArgument(0));
        service.createProduct(userId,request("SHARED","Tea A",categoryId));service.createProduct(otherUser,request("SHARED","Tea B",secondCategory.getId()));
        org.mockito.ArgumentCaptor<Product> captor=org.mockito.ArgumentCaptor.forClass(Product.class);verify(products,times(2)).saveAndFlush(captor.capture());
        assertEquals(Set.of(businessId,otherBusinessId),captor.getAllValues().stream().map(p->p.getDistributor().getId()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void duplicateSkuWithinBusinessIsRejectedBeforeSave(){
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.existsByDistributorIdAndSkuIgnoreCase(businessId,"SKU-1")).thenReturn(true);
        assertThrows(ResourceConflictException.class,()->service.createProduct(userId,request("SKU-1","Tea",categoryId)));
        verify(products,never()).saveAndFlush(any());
    }

    @Test void anotherDistributorsCategoryOrProductIsNotVisible(){
        UUID productId=UUID.randomUUID();when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));
        when(products.findByIdAndDistributorId(productId,businessId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.getProduct(userId,productId));
        when(categories.findByIdAndDistributorIdAndActiveTrue(categoryId,businessId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.createProduct(userId,request("SKU-2","Tea",categoryId)));
        verify(products,never()).saveAndFlush(any());
    }

    @Test void userWithoutActiveBusinessMembershipIsDenied(){
        when(authorization.activeOrderDeskBusinessIds(userId)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class,()->service.listProducts(userId,null,null));verifyNoInteractions(products,categories,inventory);
    }

    @Test void authorizedProductCanBeUpdatedWithoutChangingItsBusiness(){
        Product p=new Product();p.setId(UUID.randomUUID());p.setDistributor(business(businessId));p.setCategory(category(categoryId,businessId));p.setSku("OLD");p.setName("Old tea");p.setUnitName("g");p.setUnitQuantity(BigDecimal.ONE);p.setUnitsPerCase(1);p.setActive(true);p.setCreatedAt(Instant.now());
        Inventory stock=new Inventory();stock.setAvailableCases(8);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(p.getId(),businessId)).thenReturn(Optional.of(p));
        when(products.existsByDistributorIdAndSkuIgnoreCaseAndIdNot(businessId,"NEW",p.getId())).thenReturn(false);when(categories.findByIdAndDistributorIdAndActiveTrue(categoryId,businessId)).thenReturn(Optional.of(category(categoryId,businessId)));
        when(products.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));when(inventory.findByProductIdAndDistributorId(p.getId(),businessId)).thenReturn(Optional.of(stock));
        var result=service.updateProduct(userId,p.getId(),request("NEW","New tea",categoryId));
        assertEquals("NEW",result.sku());assertEquals("New tea",result.name());assertEquals(8,result.availableCases());assertEquals(businessId,p.getDistributor().getId());
    }

    @Test void deactivationPreservesProductInsteadOfDeletingHistoricalReference(){
        Product p=new Product();p.setId(UUID.randomUUID());p.setDistributor(business(businessId));p.setCategory(category(categoryId,businessId));p.setSku("SKU-OLD");p.setName("Historic product");p.setUnitName("case");p.setUnitQuantity(BigDecimal.ONE);p.setUnitsPerCase(1);p.setActive(true);p.setCreatedAt(Instant.now());
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(businessId));when(products.findByIdAndDistributorId(p.getId(),businessId)).thenReturn(Optional.of(p));
        when(products.save(any())).thenAnswer(inv->inv.getArgument(0));when(inventory.findByProductIdAndDistributorId(p.getId(),businessId)).thenReturn(Optional.of(new Inventory()));
        var result=service.setProductActive(userId,p.getId(),false);
        assertFalse(result.active());verify(products).save(p);verify(products,never()).delete(any());
    }

    private ProductUpsertRequest request(String sku,String name,UUID category){return new ProductUpsertRequest(sku,name,null,"Brand",category,"g",BigDecimal.valueOf(250),40,"40 packs per case");}
    private DistributorBusiness business(UUID id){DistributorBusiness b=new DistributorBusiness();b.setId(id);return b;}
    private ProductCategory category(UUID id,UUID businessId){ProductCategory c=new ProductCategory();c.setId(id);c.setName("Tea");c.setSlug("tea");c.setActive(true);c.setDistributor(business(businessId));return c;}
}
