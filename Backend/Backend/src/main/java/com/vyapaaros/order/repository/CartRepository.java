package com.vyapaaros.order.repository;
import com.vyapaaros.order.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface CartRepository extends JpaRepository<Cart,UUID> {
 @Query("select distinct c from Cart c left join fetch c.items i join fetch c.retailer where c.createdBy.id=:userId and c.retailer.id=:retailerId and c.status=com.vyapaaros.order.entity.CartStatus.ACTIVE")
 Optional<Cart> findActive(@Param("userId") UUID userId,@Param("retailerId") UUID retailerId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Cart c where c.createdBy.id=:userId and c.retailer.id=:retailerId and c.status=com.vyapaaros.order.entity.CartStatus.ACTIVE")
 Optional<Cart> findActiveForUpdate(@Param("userId") UUID userId,@Param("retailerId") UUID retailerId);
}
