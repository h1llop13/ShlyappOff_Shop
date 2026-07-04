package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.CartItemRepository;
import com.shlyapoff.shop.repository.CartRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void addToCart(String sessionId, Long productId, int quantity) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setSessionId(sessionId);
                    return cartRepository.save(newCart);
                });

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        } else {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Товар не найден"));

            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            cartItemRepository.save(newItem);
        }
    }

    @Transactional
    public void updateQuantity(String sessionId, Long productId, int quantity) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Корзина не найдена"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new RuntimeException("Товар не найден в корзине"));

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }
    }

    @Transactional
    public void removeFromCart(String sessionId, Long productId) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Корзина не найдена"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new RuntimeException("Товар не найден в корзине"));

        cartItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public Optional<Cart> getCartBySessionId(String sessionId) {
        return cartRepository.findBySessionIdWithItems(sessionId);
    }

    @Transactional
    public void clearCart(String sessionId) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElse(null);
        if (cart != null) {
            cartRepository.delete(cart);
        }
    }
}
