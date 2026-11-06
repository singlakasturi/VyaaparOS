package com.vyapaaros.order.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderItem;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryRestorationServiceTest {
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final InventoryRestorationService service = new InventoryRestorationService(inventory);

    @Test void rejectionRestoresToTheOrderDistributorInventory() {
        UUID distributorId=UUID.randomUUID(), productId=UUID.randomUUID();
        Order order=order(distributorId, productId, distributorId, 7);
        when(inventory.restore(productId, distributorId, 7)).thenReturn(1);
        service.restore(order);
        verify(inventory).restore(productId, distributorId, 7);
    }

    @Test void refusesRestorationWhenProductAndOrderBelongToDifferentBusinesses() {
        UUID distributorA=UUID.randomUUID(), distributorB=UUID.randomUUID(), productId=UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> service.restore(order(distributorA, productId, distributorB, 3)));
        verifyNoInteractions(inventory);
    }

    private Order order(UUID orderDistributor, UUID productId, UUID productDistributor, int quantity) {
        DistributorBusiness a=new DistributorBusiness(); a.setId(orderDistributor);
        DistributorBusiness b=new DistributorBusiness(); b.setId(productDistributor);
        Product product=new Product(); product.setId(productId); product.setDistributor(b);
        Order order=new Order(); order.setDistributor(a);
        OrderItem item=new OrderItem(); item.setProduct(product); item.setTotalCases(quantity); order.addItem(item);
        return order;
    }
}
