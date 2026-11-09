package com.vyapaaros.inventory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.inventory.entity.*;
import com.vyapaaros.inventory.repository.*;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class InventoryMovementServiceTest {
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
    private final InventoryMovementService service = new InventoryMovementService(inventory, movements);
    private final UUID productId = UUID.randomUUID(), distributorId = UUID.randomUUID(), inventoryId = UUID.randomUUID();

    @Test void reservationReleaseAndDeliveryRecordDistinctPhysicalAndReservedEffects() {
        Product product = new Product(); product.setId(productId);
        DistributorBusiness business = new DistributorBusiness(); business.setId(distributorId);
        User actor = new User(); actor.setId(UUID.randomUUID());
        Order order = new Order(); order.setId(UUID.randomUUID()); order.setOrderNumber("ORD-7"); order.setDistributor(business); order.setCreatedBy(actor);
        OrderItem line = new OrderItem(); line.setProduct(product); line.setTotalCases(4); order.addItem(line);
        when(movements.existsByEventKey(anyString())).thenReturn(false);
        InventoryBalanceView snapshot=balance(20,4,16);
        when(inventory.findBalanceByProductIdAndDistributorId(productId, distributorId)).thenReturn(Optional.of(snapshot));
        when(inventory.getReferenceById(inventoryId)).thenReturn(inventoryRow(product,business));

        service.orderReservation(order, actor);
        service.reservationRelease(order, actor, "Rejected order");
        service.deliveryConsumption(order, actor);

        ArgumentCaptor<InventoryMovement> captured = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements, times(3)).save(captured.capture());
        var events=captured.getAllValues();
        assertEquals(InventoryMovementType.ORDER_RESERVATION,events.get(0).getMovementType());
        assertEquals(0,events.get(0).getOnHandDelta());assertEquals(4,events.get(0).getReservedDelta());
        assertEquals(20,events.get(0).getOnHandAfter());assertEquals(4,events.get(0).getReservedAfter());assertEquals(16,events.get(0).getAvailableAfter());
        assertEquals(InventoryMovementType.RESERVATION_RELEASE,events.get(1).getMovementType());
        assertEquals(0,events.get(1).getOnHandDelta());assertEquals(-4,events.get(1).getReservedDelta());
        assertEquals(InventoryMovementType.DELIVERY_CONSUMPTION,events.get(2).getMovementType());
        assertEquals(-4,events.get(2).getOnHandDelta());assertEquals(-4,events.get(2).getReservedDelta());
        events.forEach(event -> assertEquals(actor,event.getActor()));
    }

    @Test void recordsManualAdjustmentAndOpeningSnapshotWithoutInventingDeltas() {
        Product product = new Product(); product.setId(productId);
        DistributorBusiness business = new DistributorBusiness(); business.setId(distributorId);
        Inventory row=inventoryRow(product,business);row.setOnHandCases(15);row.setReservedCases(2);row.setAvailableCases(13);
        User actor=new User();actor.setId(UUID.randomUUID());
        InventoryAdjustment adjustment=new InventoryAdjustment();adjustment.setId(UUID.randomUUID());adjustment.setInventory(row);adjustment.setProduct(product);adjustment.setDistributor(business);adjustment.setActor(actor);adjustment.setQuantityDelta(3);adjustment.setQuantityAfter(13);adjustment.setReason("Count correction");
        when(movements.existsByEventKey(anyString())).thenReturn(false);
        service.openingBalance(row,"Initial stock baseline");service.manualAdjustment(adjustment);
        ArgumentCaptor<InventoryMovement> captured=ArgumentCaptor.forClass(InventoryMovement.class);verify(movements,times(2)).save(captured.capture());
        assertEquals(InventoryMovementType.OPENING_BALANCE,captured.getAllValues().get(0).getMovementType());
        assertEquals(0,captured.getAllValues().get(0).getOnHandDelta());
        var manual=captured.getAllValues().get(1);assertEquals(InventoryMovementType.MANUAL_ADJUSTMENT,manual.getMovementType());
        assertEquals(3,manual.getOnHandDelta());assertEquals(0,manual.getReservedDelta());assertEquals(15,manual.getOnHandAfter());
        assertEquals("Count correction",manual.getReason());assertEquals(actor,manual.getActor());
    }

    @Test void uniqueEventKeyPreventsDuplicateLedgerRowsAndOperationsAreTransactional() throws Exception {
        Product product=new Product();product.setId(productId);DistributorBusiness business=new DistributorBusiness();business.setId(distributorId);
        Order order=new Order();order.setId(UUID.randomUUID());order.setDistributor(business);OrderItem item=new OrderItem();item.setProduct(product);item.setTotalCases(1);order.addItem(item);
        when(movements.existsByEventKey(anyString())).thenReturn(true);
        service.orderReservation(order,null);
        verifyNoInteractions(inventory);verify(movements,never()).save(any());
        assertNotNull(InventoryMovementService.class.getMethod("orderReservation",Order.class,User.class).getAnnotation(Transactional.class));
        assertNotNull(InventoryMovementService.class.getMethod("manualAdjustment",InventoryAdjustment.class).getAnnotation(Transactional.class));
    }

    private InventoryBalanceView balance(int onHand,int reserved,int available){InventoryBalanceView view=mock(InventoryBalanceView.class);when(view.getInventoryId()).thenReturn(inventoryId);when(view.getOnHandCases()).thenReturn(onHand);when(view.getReservedCases()).thenReturn(reserved);when(view.getAvailableCases()).thenReturn(available);return view;}
    private Inventory inventoryRow(Product product,DistributorBusiness business){Inventory row=new Inventory();row.setId(inventoryId);row.setProduct(product);row.setDistributor(business);row.setOnHandCases(20);row.setReservedCases(4);row.setAvailableCases(16);row.setUpdatedAt(Instant.now());return row;}
}
