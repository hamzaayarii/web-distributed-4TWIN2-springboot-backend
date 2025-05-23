package com.techgear.orderservice.repositories;


import com.techgear.orderservice.entities.Order;
import com.techgear.orderservice.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;


public interface OrderRepository extends JpaRepository<Order,Long> {

    List<Order> findByOrderDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    List<Order> findByUserId(Long userId);

    List<Order> findByStatus(OrderStatus status);

    long countByStatus(OrderStatus status);
}
