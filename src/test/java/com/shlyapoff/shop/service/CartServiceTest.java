package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.CartItemRepository;
import com.shlyapoff.shop.repository.CartRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для CartService.
 * Проверяем логику корзины в изоляции от базы данных — все репозитории замоканы.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private static final String SESSION_ID = "session-123";
    private static final Long PRODUCT_ID = 1L;

    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        cart = new Cart();
        cart.setId(10L);
        cart.setSessionId(SESSION_ID);

        product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Test Product");
    }

    @Nested
    @DisplayName("addToCart")
    class AddToCart {

        @Test
        @DisplayName("создаёт новую корзину, если её ещё нет, и добавляет новый товар")
        void createsNewCartAndAddsNewItem() {
            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.empty());
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

            cartService.addToCart(SESSION_ID, PRODUCT_ID, 2);

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemRepository).save(captor.capture());

            CartItem saved = captor.getValue();
            assertThat(saved.getQuantity()).isEqualTo(2);
            assertThat(saved.getProduct()).isEqualTo(product);
            assertThat(saved.getCart()).isEqualTo(cart);
            verify(cartRepository).save(any(Cart.class));
        }

        @Test
        @DisplayName("увеличивает количество, если товар уже есть в корзине")
        void increasesQuantityWhenItemAlreadyExists() {
            CartItem existingItem = new CartItem();
            existingItem.setCart(cart);
            existingItem.setProduct(product);
            existingItem.setQuantity(3);

            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.of(existingItem));

            cartService.addToCart(SESSION_ID, PRODUCT_ID, 2);

            assertThat(existingItem.getQuantity()).isEqualTo(5);
            verify(cartItemRepository).save(existingItem);
            // productRepository не должен вызываться, т.к. товар в корзине уже найден
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("выбрасывает исключение, если товара не существует в каталоге")
        void throwsWhenProductNotFound() {
            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.empty());
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addToCart(SESSION_ID, PRODUCT_ID, 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Товар не найден");

            verify(cartItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateQuantity")
    class UpdateQuantity {

        @Test
        @DisplayName("обновляет количество товара в корзине")
        void updatesQuantity() {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(1);

            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.of(item));

            cartService.updateQuantity(SESSION_ID, PRODUCT_ID, 7);

            assertThat(item.getQuantity()).isEqualTo(7);
            verify(cartItemRepository).save(item);
            verify(cartItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("удаляет товар, если новое количество <= 0")
        void removesItemWhenQuantityIsZeroOrLess() {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(1);

            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.of(item));

            cartService.updateQuantity(SESSION_ID, PRODUCT_ID, 0);

            verify(cartItemRepository).delete(item);
            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("выбрасывает исключение, если корзина не найдена")
        void throwsWhenCartNotFound() {
            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateQuantity(SESSION_ID, PRODUCT_ID, 5))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Корзина не найдена");
        }
    }

    @Nested
    @DisplayName("removeFromCart / clearCart / getCartBySessionId")
    class OtherOperations {

        @Test
        @DisplayName("удаляет товар из корзины")
        void removesItemFromCart() {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);

            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.of(item));

            cartService.removeFromCart(SESSION_ID, PRODUCT_ID);

            verify(cartItemRepository).delete(item);
        }

        @Test
        @DisplayName("clearCart ничего не делает, если корзины не существует (не бросает исключение)")
        void clearCartDoesNothingWhenCartMissing() {
            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            cartService.clearCart(SESSION_ID);

            verify(cartRepository, never()).delete(any());
        }

        @Test
        @DisplayName("clearCart удаляет корзину, если она существует")
        void clearCartDeletesExistingCart() {
            when(cartRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(cart));

            cartService.clearCart(SESSION_ID);

            verify(cartRepository).delete(cart);
        }

        @Test
        @DisplayName("getCartBySessionId возвращает корзину с товарами через fetch-запрос")
        void getCartBySessionIdReturnsCartWithItems() {
            when(cartRepository.findBySessionIdWithItems(SESSION_ID)).thenReturn(Optional.of(cart));

            Optional<Cart> result = cartService.getCartBySessionId(SESSION_ID);

            assertThat(result).isPresent().contains(cart);
        }
    }
}