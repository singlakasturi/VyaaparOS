package com.vyapaaros.order.repository;
import com.vyapaaros.order.entity.CartItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CartItemRepository extends JpaRepository<CartItem,UUID> {
 Optional<CartItem> findByIdAndCartId(UUID id,UUID cartId);
 Optional<CartItem> findByCartIdAndProductId(UUID cartId,UUID productId);
}
