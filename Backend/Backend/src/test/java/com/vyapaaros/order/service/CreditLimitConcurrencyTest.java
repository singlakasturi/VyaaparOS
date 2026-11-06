package com.vyapaaros.order.service;

import com.vyapaaros.retailer.repository.RetailerRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-level regression tests for the credit-limit concurrency contract.
 *
 * NOTE: These tests verify that the correct pessimistic-locking annotation is present
 * on the repository. They do NOT exercise actual database locking behaviour.
 *
 * Database-backed concurrency integration tests would require a running PostgreSQL
 * instance with two concurrent transactions; this is out of scope for the sandboxed
 * test environment but the locking mechanism is in place via PESSIMISTIC_WRITE.
 */
class CreditLimitConcurrencyTest {

    /**
     * The retailer row must be locked with PESSIMISTIC_WRITE before the credit
     * check in OrderService.place(). This prevents two concurrent transactions
     * from both reading the same outstanding_balance and both concluding that
     * the new order fits within the limit.
     *
     * Mechanism: RetailerRepository.findById() carries @Lock(PESSIMISTIC_WRITE).
     * OrderService.place() calls retailers.findById() inside a @Transactional
     * method, so the row-level lock is held for the entire transaction including
     * the credit calculation and order insertion.
     */
    @Test
    void retailerRepositoryFindByIdHasPessimisticWriteLock() throws Exception {
        Method findById = RetailerRepository.class.getMethod("findById", Object.class);
        Lock lockAnnotation = findById.getAnnotation(Lock.class);

        assertNotNull(lockAnnotation,
            "RetailerRepository.findById must carry @Lock to serialize concurrent credit checks.");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lockAnnotation.value(),
            "Lock mode must be PESSIMISTIC_WRITE so concurrent transactions block until the " +
            "first completes and releases the retailer row lock.");
    }

    /**
     * OrderService.place() must be transactional so that the pessimistic lock
     * acquired on the retailer row (via findById) is held through credit validation
     * and order persistence.
     */
    @Test
    void orderServicePlaceIsAnnotatedTransactional() throws Exception {
        Method placeMethod = OrderService.class.getMethod(
            "place",
            org.springframework.security.core.Authentication.class,
            com.vyapaaros.order.dto.OrderRequest.class
        );
        org.springframework.transaction.annotation.Transactional txAnnotation =
            placeMethod.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertNotNull(txAnnotation,
            "OrderService.place() must be @Transactional so the PESSIMISTIC_WRITE lock on the " +
            "retailer row is held for the full duration of the credit check and order save.");
    }
}
