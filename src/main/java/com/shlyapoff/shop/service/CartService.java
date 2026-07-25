package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.model.VariantType;
import com.shlyapoff.shop.repository.CartItemRepository;
import com.shlyapoff.shop.repository.CartRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
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
    private final ProductVariantRepository productVariantRepository;

    @Transactional
    public void addToCart(String sessionId, Long productId, int quantity) {
        addToCart(sessionId, productId, null, quantity);
    }

    @Transactional
    public void addToCart(String sessionId, Long productId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество товара должно быть больше нуля");
        }

        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setSessionId(sessionId);
                    return cartRepository.save(newCart);
                });

        Optional<CartItem> existingItem = findCartItem(cart.getId(), productId, variantId);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int requestedQuantity = item.getQuantity() + quantity;
            validateProductAvailability(item.getProduct(), variantId, requestedQuantity);
            item.setQuantity(requestedQuantity);
            cartItemRepository.save(item);
        } else {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
            ProductVariant variant = validateProductAvailability(product, variantId, quantity);
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setProductVariant(variant);
            newItem.setVariantKey(variant == null ? 0L : variant.getId());
            newItem.setQuantity(quantity);
            cartItemRepository.save(newItem);
        }
    }

    @Transactional
    public void updateQuantity(String sessionId, Long productId, int quantity) {
        updateQuantity(sessionId, productId, null, quantity);
    }

    @Transactional
    public void updateQuantity(String sessionId, Long productId, Long variantId, int quantity) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Корзина не найдена"));

        CartItem item = findCartItem(cart.getId(), productId, variantId)
                .orElseThrow(() -> new RuntimeException("Товар не найден в корзине"));

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            validateProductAvailability(item.getProduct(), variantId, quantity);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }
    }

    @Transactional
    public void removeFromCart(String sessionId, Long productId) {
        removeFromCart(sessionId, productId, null);
    }

    @Transactional
    public void removeFromCart(String sessionId, Long productId, Long variantId) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Корзина не найдена"));

        CartItem item = findCartItem(cart.getId(), productId, variantId)
                .orElseThrow(() -> new RuntimeException("Товар не найден в корзине"));

        cartItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public Optional<Cart> getCartBySessionId(String sessionId) {
        return cartRepository.findBySessionIdWithItems(sessionId);
    }

    @Transactional
    public Optional<Cart> getCartBySessionIdForCheckout(String sessionId) {
        return cartRepository.findBySessionIdWithItemsForUpdate(sessionId);
    }

    @Transactional
    public void clearCart(String sessionId) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElse(null);
        if (cart != null) {
            cartRepository.delete(cart);
        }
    }

    public void validateCartItemAvailability(CartItem item) {
        Product product = productRepository.findById(item.getProduct().getId())
                .orElseThrow(() -> new IllegalStateException("Товар больше недоступен"));
        validateProductAvailability(product,
                item.getProductVariant() == null ? null : item.getProductVariant().getId(),
                item.getQuantity());
    }

    private ProductVariant validateProductAvailability(Product product, Long variantId, int requestedQuantity) {
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalStateException("Товар больше недоступен");
        }

        boolean requiresVariant = product.getCategory() != null
                && product.getCategory().getVariantType() != null
                && product.getCategory().getVariantType() != VariantType.NONE;
        if (!requiresVariant) {
            if (variantId != null) {
                throw new IllegalArgumentException("Для этого товара вариант не выбирается");
            }
            int availableQuantity = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (availableQuantity < requestedQuantity) {
                throw new IllegalStateException("Недостаточно товара на складе. Доступно: " + availableQuantity);
            }
            return null;
        }

        if (variantId == null) {
            throw new IllegalArgumentException("Выберите вариант товара");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Вариант товара не найден"));
        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new IllegalArgumentException("Выбранный вариант не принадлежит товару");
        }
        int availableQuantity = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
        if (availableQuantity < requestedQuantity) {
            throw new IllegalStateException("Выбранного варианта недостаточно. Доступно: " + availableQuantity);
        }
        return variant;
    }

    private Optional<CartItem> findCartItem(Long cartId, Long productId, Long variantId) {
        if (variantId == null) {
            return cartItemRepository.findByCartIdAndProductId(cartId, productId);
        }
        return cartItemRepository.findByCartIdAndProductIdAndVariantId(cartId, productId, variantId);
    }
}
