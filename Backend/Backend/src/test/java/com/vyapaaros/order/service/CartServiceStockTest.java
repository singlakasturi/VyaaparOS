package com.vyapaaros.order.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.entity.ProductCategory;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.InsufficientStockException;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.order.dto.CartItemRequest;
import com.vyapaaros.order.repository.CartItemRepository;
import com.vyapaaros.order.repository.CartRepository;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.pricing.service.PriceResolutionService;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.schemes.dto.SchemeEvaluation;
import com.vyapaaros.schemes.service.SchemeResolutionService;
import com.vyapaaros.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CartServiceStockTest {
    @Test
    void cartRejectsUserWithoutActiveRetailerMembershipBeforeProductLookup() {
        CartRepository carts=mock(CartRepository.class); CartItemRepository items=mock(CartItemRepository.class);
        ProductRepository products=mock(ProductRepository.class); RetailerRepository retailers=mock(RetailerRepository.class);
        UserRepository users=mock(UserRepository.class); OrderScopeService scope=mock(OrderScopeService.class);
        InventoryRepository inventory=mock(InventoryRepository.class); PriceResolutionService prices=mock(PriceResolutionService.class);
        SchemeResolutionService schemes=mock(SchemeResolutionService.class);
        UUID userId=UUID.randomUUID(), retailerId=UUID.randomUUID(), productId=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(userId,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RETAILER")));
        when(scope.userId(auth)).thenReturn(userId);
        doThrow(new org.springframework.security.access.AccessDeniedException("no membership"))
            .when(scope).require(userId,retailerId,auth);
        CartService service=new CartService(carts,items,products,retailers,users,scope,inventory,prices,schemes);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            ()->service.upsert(auth,new CartItemRequest(retailerId,productId,1)));
        verifyNoInteractions(products,inventory,prices,schemes);
    }

    @Test
    void outOfStockProductIsRejectedBeforeCartIsCreatedOrChanged() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        RetailerRepository retailers = mock(RetailerRepository.class);
        UserRepository users = mock(UserRepository.class);
        OrderScopeService scope = mock(OrderScopeService.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        PriceResolutionService prices = mock(PriceResolutionService.class);
        SchemeResolutionService schemes = mock(SchemeResolutionService.class);
        UUID retailerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DistributorBusiness distributor = new DistributorBusiness(); distributor.setId(UUID.randomUUID());
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RETAILER")));
        Retailer retailer = new Retailer();
        retailer.setStatus(RetailerStatus.ACTIVE);
        when(retailers.findById(retailerId)).thenReturn(Optional.of(retailer));
        ProductCategory category = new ProductCategory();
        category.setActive(true);
        Product product = new Product();
        product.setId(productId);
        product.setSku("SKU-TE-250");
        product.setName("Tea leaf 250 g");
        product.setCategory(category);
        product.setDistributor(distributor);
        when(scope.currentDistributorForRetailer(retailerId)).thenReturn(distributor);
        when(products.findByIdAndDistributorIdAndActiveTrue(productId, distributor.getId())).thenReturn(Optional.of(product));
        when(prices.resolve(eq(auth), eq(retailerId), anyList())).thenReturn(java.util.Map.of(productId, ResolvedPrice.unavailable()));
        when(schemes.evaluate(eq(productId), eq(retailerId), eq(1), any(), eq(auth)))
            .thenReturn(SchemeEvaluation.unavailable(1));
        when(inventory.findByProductIdAndDistributorId(productId, distributor.getId())).thenReturn(Optional.empty());
        when(scope.userId(auth)).thenReturn(userId);

        CartService service = new CartService(carts, items, products, retailers, users, scope, inventory, prices, schemes);
        InsufficientStockException error = assertThrows(InsufficientStockException.class,
            () -> service.upsert(auth, new CartItemRequest(retailerId, productId, 1)));

        assertEquals("SKU-TE-250", error.items().getFirst().sku());
        assertEquals(0, error.items().getFirst().availableCases());
        verify(carts, never()).findActiveForUpdate(any(), any());
        verify(carts, never()).save(any());
    }
}
