
package com.techgear.orderservice.api;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.techgear.orderservice.clients.ProductClient;
import com.techgear.orderservice.dto.CheckoutRequest;
import com.techgear.orderservice.dto.Product;
import com.techgear.orderservice.entities.Order;
import com.techgear.orderservice.entities.OrderItems;
import com.techgear.orderservice.entities.OrderStatus;
import com.techgear.orderservice.entities.PaymentMethod;
import com.techgear.orderservice.services.IOrderService;
import com.techgear.orderservice.services.StripeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class StripeController {

    private final StripeService stripeService;
    @Autowired
    private ProductClient productClient;
    @Autowired
    private IOrderService orderService;

    // Store session ID to order ID mapping for tracking
    private final Map<String, Long> sessionToOrderMap = new HashMap<>();

    @PostMapping("/create-checkout-session")
    public ResponseEntity<?> createCheckoutSession(@RequestBody CheckoutRequest request) throws StripeException {
        // Check if products are available in the request
        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            return ResponseEntity.badRequest().body("No products in checkout request");
        }

        // Create an order first
        Order order = new Order();
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PROCESSING); // Start with PROCESSING status
        order.setUser(request.getUser());
        order.setShippingAddress(request.getShippingAddress());
        order.setBillingAddress(request.getBillingAddress());
        order.setPaymentMethod(PaymentMethod.CREDIT_CARD); // Since using Stripe
        order.setOrderNumber("ORD-" + System.currentTimeMillis());


        List<OrderItems> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;


        List<Product> enrichedProducts = new ArrayList<>();

        for (Product productRequest : request.getProducts()) {
            // Fetch the complete product details from the database
            Product fullProduct = productClient.getProductById(productRequest.getId());

            if (fullProduct == null) {
                return ResponseEntity.badRequest().body("Product with ID " + productRequest.getId() + " not found");
            }

            // Set the requested quantity to the fetched product
            fullProduct.setStock(productRequest.getStock());

            // Add to enriched products list for Stripe
            enrichedProducts.add(fullProduct);

            // Check stock availability
            if (fullProduct.getStock() < productRequest.getStock()) {
                return ResponseEntity.badRequest().body(
                        "Not enough stock for product: " + fullProduct.getName() +
                                ". Available: " + fullProduct.getStock() +
                                ", Requested: " + productRequest.getStock());
            }

            // Create order item
            OrderItems item = new OrderItems();
            item.setProductId(fullProduct.getId());
            item.setProductName(fullProduct.getName());
            item.setPrice((float) fullProduct.getPrice());
            item.setQuantity(BigDecimal.valueOf(productRequest.getStock())); // Using the requested quantity
            item.setOrder(order);

            // Add to order items list
            orderItems.add(item);

            // Update total
            BigDecimal itemTotal = BigDecimal.valueOf(fullProduct.getPrice())
                    .multiply(BigDecimal.valueOf(productRequest.getStock()));
            totalAmount = totalAmount.add(itemTotal);
        }

        // Set order properties
        order.setOrderItemsList(orderItems);
        order.setTotalAmount(totalAmount);
        order.setOrderNumber("ORD-" + System.currentTimeMillis()); // Generate a unique order number

        // Save the order first
        Order savedOrder = orderService.createOrder(order);

        // Create a new CheckoutRequest with the enriched products
        CheckoutRequest enrichedRequest = new CheckoutRequest();
        enrichedRequest.setProducts(enrichedProducts);
        enrichedRequest.setUser(request.getUser());

        // Create checkout session with the enriched products
        Session session = stripeService.createCheckoutSession(enrichedRequest);

        // Store the mapping between session ID and order ID
        sessionToOrderMap.put(session.getId(), savedOrder.getId());

        // Return session URL and order ID to frontend
        Map<String, Object> response = new HashMap<>();
        response.put("sessionUrl", session.getUrl());
        response.put("sessionId", session.getId());
        response.put("orderId", savedOrder.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/success")
    public String paymentSuccess(@RequestParam("session_id") String sessionId) {
        // Get the order ID associated with this session
        Long orderId = sessionToOrderMap.get(sessionId);

        if (orderId == null) {
            return "Error: Order not found for this session";
        }

        // Get the order
        Order order = orderService.getOrderById(orderId);

        if (order == null) {
            return "Error: Order not found";
        }

        // Update order status
        order.setStatus(OrderStatus.DELIVERED);
        orderService.updateOrder(order.getId(), order);

        // Reduce product stock
        for (OrderItems item : order.getOrderItemsList()) {
            productClient.reduceStock(item.getProductId(), item.getQuantity().intValue());
        }

        // Remove the session mapping
        sessionToOrderMap.remove(sessionId);

        return "Payment successful! Your order #" + order.getOrderNumber() + " has been confirmed.";
    }

    @GetMapping("/cancel")
    public String paymentCancelled(@RequestParam("session_id") String sessionId) {
        // Get the order ID associated with this session
        Long orderId = sessionToOrderMap.get(sessionId);

        if (orderId != null) {
            // Get the order and mark it as cancelled
            Order order = orderService.getOrderById(orderId);
            if (order != null) {
                order.setStatus(OrderStatus.CANCELLED);
                orderService.updateOrder(order.getId(), order);
            }

            // Remove the session mapping
            sessionToOrderMap.remove(sessionId);
        }

        return "Payment cancelled. You can try again later.";
    }
}