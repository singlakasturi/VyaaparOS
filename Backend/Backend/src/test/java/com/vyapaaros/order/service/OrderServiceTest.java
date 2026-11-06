package com.vyapaaros.order.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.entity.ProductCategory;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.inventory.entity.Inventory;
import com.vyapaaros.common.exception.InsufficientStockException;
import com.vyapaaros.order.dto.OrderItemRequest;
import com.vyapaaros.order.dto.OrderRequest;
import com.vyapaaros.order.dto.OrderResponse;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderStatus;
import com.vyapaaros.order.repository.CartRepository;
import com.vyapaaros.order.repository.OrderRepository;
import com.vyapaaros.pricing.dto.PriceSource;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.pricing.service.PriceResolutionService;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.schemes.dto.SchemeEvaluation;
import com.vyapaaros.schemes.entity.SchemeType;
import com.vyapaaros.schemes.service.SchemeResolutionService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CartRepository cartRepository = mock(CartRepository.class);
    private final RetailerRepository retailerRepository = mock(RetailerRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OrderScopeService orderScopeService = mock(OrderScopeService.class);
    private final PriceResolutionService priceResolutionService = mock(PriceResolutionService.class);
    private final SchemeResolutionService schemeResolutionService = mock(SchemeResolutionService.class);
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);

    private OrderService orderService;

    private final UUID salespersonId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID clientOrderId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken auth;
    private Retailer activeRetailer;
    private DistributorBusiness distributor;
    private User salespersonUser;
    private Product activeProduct;

    @BeforeEach
    void setUp() {
        reset(
            orderRepository,
            cartRepository,
            retailerRepository,
            userRepository,
            orderScopeService,
            priceResolutionService,
            schemeResolutionService,
            inventoryRepository,
            productRepository
        );

        orderService = new OrderService(
            orderRepository,
            cartRepository,
            retailerRepository,
            userRepository,
            orderScopeService,
            priceResolutionService,
            schemeResolutionService,
            inventoryRepository,
            productRepository
        );

        auth = new UsernamePasswordAuthenticationToken(
            salespersonId,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_RETAILER"))
        );

        when(orderScopeService.userId(auth)).thenReturn(salespersonId);

        activeRetailer = new Retailer();
        activeRetailer.setId(retailerId);
        activeRetailer.setBusinessName("Test Retailer");
        activeRetailer.setStatus(RetailerStatus.ACTIVE);
        activeRetailer.setCreditLimit(new BigDecimal("50000.00"));
        activeRetailer.setOutstandingBalance(new BigDecimal("1000.00"));

        distributor = new DistributorBusiness();
        distributor.setId(UUID.randomUUID());
        when(orderScopeService.currentDistributorForRetailer(retailerId)).thenReturn(distributor);

        salespersonUser = new User();
        salespersonUser.setId(salespersonId);

        ProductCategory category = new ProductCategory();
        category.setActive(true);

        activeProduct = new Product();
        activeProduct.setId(productId);
        activeProduct.setSku("SKU-P1");
        activeProduct.setName("Parle-G 70g");
        activeProduct.setActive(true);
        activeProduct.setCategory(category);
        activeProduct.setDistributor(distributor);

        Inventory stock = new Inventory();
        stock.setProduct(activeProduct);
        stock.setAvailableCases(100);
        when(inventoryRepository.findByProductIdInAndDistributorId(anyCollection(), eq(distributor.getId()))).thenReturn(List.of(stock));

        when(retailerRepository.findById(retailerId)).thenReturn(Optional.of(activeRetailer));
        when(userRepository.findById(salespersonId)).thenReturn(Optional.of(salespersonUser));
        when(productRepository.findByIdAndDistributorIdAndActiveTrue(productId, distributor.getId())).thenReturn(Optional.of(activeProduct));
        when(orderRepository.nextNumber()).thenReturn(1001L);
    }

    @Test
    void placeOrder_belowConfiguredSeedCreditLimit_isSubmitted() {
        activeRetailer.setCreditLimit(new BigDecimal("1000.00"));
        activeRetailer.setOutstandingBalance(BigDecimal.ZERO);
        when(orderRepository.findByRetailerIdAndClientOrderId(retailerId, clientOrderId))
            .thenReturn(Optional.empty());

        ResolvedPrice resolvedPrice = new ResolvedPrice(
            true,
            new BigDecimal("120.00"),
            "INR",
            PriceSource.DEFAULT_DEALER_PRICE,
            true
        );
        when(priceResolutionService.resolve(eq(auth), eq(retailerId), anyList()))
            .thenReturn(Map.of(productId, resolvedPrice));

        SchemeEvaluation evaluation = new SchemeEvaluation(
            UUID.randomUUID(),
            "Standard Scheme",
            SchemeType.BUY_X_GET_Y_FREE,
            5,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("600.00"),
            new BigDecimal("600.00"),
            "No scheme applied",
            true
        );
        when(schemeResolutionService.evaluate(eq(productId), eq(retailerId), eq(5), any(), eq(auth)))
            .thenReturn(evaluation);

        when(inventoryRepository.reserve(productId, distributor.getId(), 5)).thenReturn(1);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        OrderRequest request = new OrderRequest(
            retailerId,
            clientOrderId,
            "Deliver before noon",
            List.of(new OrderItemRequest(productId, 5))
        );

        OrderResponse response = orderService.place(auth, request);

        assertNotNull(response);
        assertEquals("ORD-1001", response.orderNumber());
        assertEquals(OrderStatus.SUBMITTED, response.status());
        assertEquals(new BigDecimal("600.00"), response.finalAmount());
        verify(orderRepository).save(argThat(order -> order.getDistributor() == distributor));
        verify(inventoryRepository).reserve(productId, distributor.getId(), 5);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void placeOrder_withDirectItems_idempotent() {
        Order existingOrder = new Order();
        existingOrder.setId(UUID.randomUUID());
        existingOrder.setOrderNumber("ORD-1000");
        existingOrder.setClientOrderId(clientOrderId);
        existingOrder.setRetailer(activeRetailer);
        existingOrder.setDistributor(distributor);
        existingOrder.setCreatedBy(salespersonUser);
        existingOrder.setStatus(OrderStatus.SUBMITTED);
        existingOrder.setFinalAmount(new BigDecimal("600.00"));
        existingOrder.setBaseAmount(new BigDecimal("600.00"));
        existingOrder.setDiscountAmount(BigDecimal.ZERO);
        existingOrder.setCurrency("INR");
        existingOrder.setCreatedAt(Instant.now());

        when(orderRepository.findByRetailerIdAndClientOrderId(retailerId, clientOrderId))
            .thenReturn(Optional.of(existingOrder));
        when(orderRepository.findDetailed(existingOrder.getId()))
            .thenReturn(Optional.of(existingOrder));

        OrderRequest request = new OrderRequest(
            retailerId,
            clientOrderId,
            "Duplicate attempt",
            List.of(new OrderItemRequest(productId, 5))
        );

        OrderResponse response = orderService.place(auth, request);

        assertNotNull(response);
        assertEquals("ORD-1000", response.orderNumber());
        assertEquals(existingOrder.getId(), response.id());
        // Verify inventory reservation was NOT called again
        verify(inventoryRepository, never()).reserve(any(), any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_withDirectItems_insufficientStock_throwsConflict() {
        when(orderRepository.findByRetailerIdAndClientOrderId(retailerId, clientOrderId))
            .thenReturn(Optional.empty());

        ResolvedPrice resolvedPrice = new ResolvedPrice(
            true,
            new BigDecimal("120.00"),
            "INR",
            PriceSource.DEFAULT_DEALER_PRICE,
            true
        );
        when(priceResolutionService.resolve(eq(auth), eq(retailerId), anyList()))
            .thenReturn(Map.of(productId, resolvedPrice));

        SchemeEvaluation evaluation = new SchemeEvaluation(
            UUID.randomUUID(),
            "Standard Scheme",
            SchemeType.BUY_X_GET_Y_FREE,
            50,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("6000.00"),
            new BigDecimal("6000.00"),
            "No scheme",
            true
        );
        when(schemeResolutionService.evaluate(eq(productId), eq(retailerId), eq(50), any(), eq(auth)))
            .thenReturn(evaluation);

        // Atomic reservation loses a race after the stock precheck.
        when(inventoryRepository.reserve(productId, distributor.getId(), 50)).thenReturn(0);
        when(inventoryRepository.findAvailableCasesByProductIdAndDistributorId(productId, distributor.getId())).thenReturn(Optional.of(0));

        OrderRequest request = new OrderRequest(
            retailerId,
            clientOrderId,
            "Stock unavailable",
            List.of(new OrderItemRequest(productId, 50))
        );

        InsufficientStockException ex = assertThrows(
            InsufficientStockException.class,
            () -> orderService.place(auth, request)
        );
        assertTrue(ex.getMessage().contains("SKU-P1"));
        assertTrue(ex.getMessage().contains("available"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_withUnavailableProduct_rejectsBeforeReservationOrOrderWrite() {
        when(orderRepository.findByRetailerIdAndClientOrderId(retailerId, clientOrderId)).thenReturn(Optional.empty());
        ResolvedPrice resolvedPrice = new ResolvedPrice(true, new BigDecimal("120.00"), "INR", PriceSource.DEFAULT_DEALER_PRICE, true);
        when(priceResolutionService.resolve(eq(auth), eq(retailerId), anyList())).thenReturn(Map.of(productId, resolvedPrice));
        SchemeEvaluation evaluation = new SchemeEvaluation(null, null, null, 1, 0, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("120.00"), new BigDecimal("120.00"), "No scheme", true);
        when(schemeResolutionService.evaluate(eq(productId), eq(retailerId), eq(1), any(), eq(auth))).thenReturn(evaluation);
        Inventory emptyStock = new Inventory();
        emptyStock.setProduct(activeProduct);
        emptyStock.setAvailableCases(0);
        when(inventoryRepository.findByProductIdInAndDistributorId(anyCollection(), eq(distributor.getId()))).thenReturn(List.of(emptyStock));

        OrderRequest request = new OrderRequest(retailerId, clientOrderId, "", List.of(new OrderItemRequest(productId, 1)));
        InsufficientStockException exception = assertThrows(InsufficientStockException.class, () -> orderService.place(auth, request));

        assertEquals(1, exception.items().size());
        assertEquals("SKU-P1", exception.items().getFirst().sku());
        assertEquals(1, exception.items().getFirst().requestedCases());
        assertEquals(0, exception.items().getFirst().availableCases());
        verify(inventoryRepository, never()).reserve(any(), any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void productFromAnotherDistributorIsDeniedBeforePricingOrStockMutation() {
        when(orderRepository.findByRetailerIdAndClientOrderId(retailerId, clientOrderId)).thenReturn(Optional.empty());
        when(productRepository.findByIdAndDistributorIdAndActiveTrue(productId, distributor.getId())).thenReturn(Optional.empty());
        OrderRequest request = new OrderRequest(retailerId, clientOrderId, "cross business",
            List.of(new OrderItemRequest(productId, 1)));
        assertThrows(com.vyapaaros.common.exception.ResourceNotFoundException.class, () -> orderService.place(auth, request));
        verifyNoInteractions(priceResolutionService, schemeResolutionService, inventoryRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_unassignedSalesperson_throwsAccessDenied() {
        doThrow(new AccessDeniedException("Access denied"))
            .when(orderScopeService).require(salespersonId, retailerId, auth);

        OrderRequest request = new OrderRequest(
            retailerId,
            clientOrderId,
            "Unauthorized",
            List.of(new OrderItemRequest(productId, 2))
        );

        assertThrows(AccessDeniedException.class, () -> orderService.place(auth, request));
    }

    @Test
    void placeOrder_exceedsCreditLimit_marksCreditHold() {
        when(orderRepository.findByRetailerIdAndClientOrderId(retailerId, clientOrderId))
            .thenReturn(Optional.empty());

        activeRetailer.setCreditLimit(new BigDecimal("10000.00"));
        activeRetailer.setOutstandingBalance(new BigDecimal("8000.00"));

        ResolvedPrice resolvedPrice = new ResolvedPrice(
            true,
            new BigDecimal("5000.00"),
            "INR",
            PriceSource.DEFAULT_DEALER_PRICE,
            true
        );
        when(priceResolutionService.resolve(eq(auth), eq(retailerId), anyList()))
            .thenReturn(Map.of(productId, resolvedPrice));

        SchemeEvaluation evaluation = new SchemeEvaluation(
            UUID.randomUUID(),
            "Standard Scheme",
            SchemeType.BUY_X_GET_Y_FREE,
            1,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("5000.00"),
            new BigDecimal("5000.00"),
            "No scheme",
            true
        );
        when(schemeResolutionService.evaluate(eq(productId), eq(retailerId), eq(1), any(), eq(auth)))
            .thenReturn(evaluation);

        when(inventoryRepository.reserve(productId, distributor.getId(), 1)).thenReturn(1);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        OrderRequest request = new OrderRequest(
            retailerId,
            clientOrderId,
            "Over credit limit",
            List.of(new OrderItemRequest(productId, 1))
        );

        OrderResponse response = orderService.place(auth, request);

        assertNotNull(response);
        assertEquals(OrderStatus.CREDIT_HOLD, response.status());
        assertTrue(response.creditHoldReason().contains("13000.00 INR"));
        assertTrue(response.creditHoldReason().contains("10000.00 INR"));
    }
}
