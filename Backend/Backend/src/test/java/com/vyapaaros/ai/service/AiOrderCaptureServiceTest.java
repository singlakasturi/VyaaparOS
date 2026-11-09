package com.vyapaaros.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.vyapaaros.ai.dto.*;
import com.vyapaaros.ai.provider.AiOrderProvider;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.order.dto.CartResponse;
import com.vyapaaros.order.service.CartService;
import com.vyapaaros.order.service.OrderScopeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AiOrderCaptureServiceTest {
    private final AiOrderProvider provider = mock(AiOrderProvider.class);
    private final ProductMatchingService matcher = mock(ProductMatchingService.class);
    private final OrderScopeService scope = mock(OrderScopeService.class);
    private final CartService cart = mock(CartService.class);
    private final AiOrderCaptureService service = new AiOrderCaptureService(provider, matcher, scope, cart);

    @Test
    void parseOnlyReturnsSuggestionsAndDoesNotTouchCart() {
        UUID retailerId = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        var auth = auth();
        var distributor = new DistributorBusiness();
        distributor.setId(distributorId);
        when(scope.currentDistributorForRetailer(retailerId)).thenReturn(distributor);
        when(provider.extract("5 peti biscuit")).thenReturn(List.of(
            new AiProviderItem("5 peti biscuit", "biscuit chhota", 5, "CASE", .94)
        ));
        ProductMatchingService.Match match = ProductMatchingService.Match.matched(product(productId));
        when(matcher.match("biscuit chhota", distributorId)).thenReturn(match);

        var response = service.parse(auth, new AiOrderParseRequest(retailerId, "5 peti biscuit"));

        assertEquals(AiParseStatus.PARSED, response.status());
        assertEquals(productId, response.items().get(0).product().id());
        verifyNoInteractions(cart);
    }

    private com.vyapaaros.catalog.entity.Product product(UUID id) {
        var product = new com.vyapaaros.catalog.entity.Product();
        product.setId(id);
        product.setSku("SKU-BS-075");
        product.setName("Glucose biscuit 75 g");
        return product;
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null,
            List.of(new SimpleGrantedAuthority("ROLE_RETAILER")));
    }
}
