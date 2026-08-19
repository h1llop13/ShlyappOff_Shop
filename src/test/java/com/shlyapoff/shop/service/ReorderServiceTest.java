package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReorderServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock CartService cartService;
    @Mock ShoppingEventService shoppingEventService;
    @InjectMocks ReorderService service;

    @Test
    void repeatsOnlyOwnersOrderUsingCurrentCartValidation() {
        Order order = order(8L);
        when(orderRepository.findByIdWithItems(4L)).thenReturn(Optional.of(order));

        int added = service.repeat(4L, 8L, "session", 99L);

        assertThat(added).isEqualTo(2);
        verify(cartService).addToCart("session", 99L, 12L, null, 2);
        verify(shoppingEventService).recordCartAdd("session", 99L, order.getItems().getFirst().getProduct());
    }

    @Test
    void rejectsForeignOrderBeforeChangingCart() {
        when(orderRepository.findByIdWithItems(4L)).thenReturn(Optional.of(order(8L)));

        assertThatThrownBy(() -> service.repeat(4L, 9L, "session", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("чужой");
        verify(cartService, never()).addToCart("session", 99L, 12L, null, 2);
    }

    private Order order(Long customerId) {
        Customer customer = new Customer();
        customer.setId(customerId);
        Product product = new Product();
        product.setId(12L);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        Order order = new Order();
        order.setCustomer(customer);
        order.addItem(item);
        return order;
    }
}
