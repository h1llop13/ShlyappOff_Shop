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

import java.util.List;
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
        addToCart(sessionId, null, productId, null, quantity);
    }

    @Transactional
    public void addToCart(String sessionId, Long productId, Long variantId, int quantity) {
        addToCart(sessionId, null, productId, variantId, quantity);
    }

    @Transactional
    public void addToCart(String sessionId, Long telegramUserId, Long productId, Long variantId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Количество товара должно быть больше нуля");
        Cart cart = resolveCart(sessionId, telegramUserId, true);
        Optional<CartItem> existingItem = findCartItem(cart.getId(), productId, variantId);
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int requestedQuantity = item.getQuantity() + quantity;
            validateProductAvailability(item.getProduct(), variantId, requestedQuantity);
            item.setQuantity(requestedQuantity);
            cartItemRepository.save(item);
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        ProductVariant variant = validateProductAvailability(product, variantId, quantity);
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setVariantKey(variant == null ? 0L : variant.getId());
        item.setQuantity(quantity);
        cartItemRepository.save(item);
    }

    @Transactional
    public void updateQuantity(String sessionId, Long productId, int quantity) {
        updateQuantity(sessionId, null, productId, null, quantity);
    }

    @Transactional
    public void updateQuantity(String sessionId, Long productId, Long variantId, int quantity) {
        updateQuantity(sessionId, null, productId, variantId, quantity);
    }

    @Transactional
    public void updateQuantity(String sessionId, Long telegramUserId, Long productId, Long variantId, int quantity) {
        CartItem item = findItem(resolveCart(sessionId, telegramUserId, false), productId, variantId);
        if (quantity <= 0) {
            cartItemRepository.delete(item);
            return;
        }
        validateProductAvailability(item.getProduct(), variantId, quantity);
        item.setQuantity(quantity);
        cartItemRepository.save(item);
    }

    @Transactional
    public void removeFromCart(String sessionId, Long productId) {
        removeFromCart(sessionId, null, productId, null);
    }

    @Transactional
    public void removeFromCart(String sessionId, Long productId, Long variantId) {
        removeFromCart(sessionId, null, productId, variantId);
    }

    @Transactional
    public void removeFromCart(String sessionId, Long telegramUserId, Long productId, Long variantId) {
        cartItemRepository.delete(findItem(resolveCart(sessionId, telegramUserId, false), productId, variantId));
    }

    @Transactional(readOnly = true)
    public Optional<Cart> getCartBySessionId(String sessionId) {
        return getCart(sessionId, null);
    }

    @Transactional(readOnly = true)
    public Optional<Cart> getCart(String sessionId, Long telegramUserId) {
        return telegramUserId == null
                ? cartRepository.findBySessionIdWithItems(sessionId)
                : cartRepository.findByTelegramUserIdWithItems(telegramUserId);
    }

    @Transactional
    public Optional<Cart> getCartBySessionIdForCheckout(String sessionId) {
        return getCartForCheckout(sessionId, null);
    }

    @Transactional
    public Optional<Cart> getCartForCheckout(String sessionId, Long telegramUserId) {
        return telegramUserId == null
                ? cartRepository.findBySessionIdWithItemsForUpdate(sessionId)
                : cartRepository.findByTelegramUserIdWithItemsForUpdate(telegramUserId);
    }

    @Transactional
    public void clearCart(String sessionId) {
        clearCart(sessionId, null);
    }

    @Transactional
    public void clearCart(String sessionId, Long telegramUserId) {
        Optional<Cart> cart = telegramUserId == null
                ? cartRepository.findBySessionId(sessionId)
                : cartRepository.findByTelegramUserId(telegramUserId);
        cart.ifPresent(cartRepository::delete);
    }

    /** Привязывает корзину сессии к Telegram и объединяет её с ранее сохранённой корзиной. */
    @Transactional
    public void bindTelegramCart(String sessionId, Long telegramUserId) {
        Cart guest = cartRepository.findBySessionId(sessionId).orElse(null);
        Cart persistent = cartRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (guest == null) return;
        if (persistent == null || persistent.getId().equals(guest.getId())) {
            guest.setTelegramUserId(telegramUserId);
            cartRepository.save(guest);
            return;
        }

        for (CartItem guestItem : List.copyOf(guest.getItems())) {
            Long variantId = guestItem.getProductVariant() == null ? null : guestItem.getProductVariant().getId();
            Optional<CartItem> existing = findCartItem(persistent.getId(), guestItem.getProduct().getId(), variantId);
            if (existing.isPresent()) {
                CartItem target = existing.get();
                target.setQuantity(target.getQuantity() + guestItem.getQuantity());
                cartItemRepository.save(target);
                cartItemRepository.delete(guestItem);
            } else {
                guestItem.setCart(persistent);
                cartItemRepository.save(guestItem);
            }
        }
        cartRepository.delete(guest);
    }

    public void validateCartItemAvailability(CartItem item) {
        Product product = productRepository.findById(item.getProduct().getId())
                .orElseThrow(() -> new IllegalStateException("Товар больше недоступен"));
        validateProductAvailability(product, item.getProductVariant() == null ? null : item.getProductVariant().getId(), item.getQuantity());
    }

    private Cart resolveCart(String sessionId, Long telegramUserId, boolean create) {
        Optional<Cart> cart = telegramUserId == null
                ? cartRepository.findBySessionId(sessionId)
                : cartRepository.findByTelegramUserId(telegramUserId);
        if (cart.isPresent()) return cart.get();
        if (!create) throw new RuntimeException("Корзина не найдена");
        Cart newCart = new Cart();
        newCart.setSessionId(sessionId);
        newCart.setTelegramUserId(telegramUserId);
        return cartRepository.save(newCart);
    }

    private CartItem findItem(Cart cart, Long productId, Long variantId) {
        return findCartItem(cart.getId(), productId, variantId)
                .orElseThrow(() -> new RuntimeException("Товар не найден в корзине"));
    }

    private Optional<CartItem> findCartItem(Long cartId, Long productId, Long variantId) {
        return variantId == null
                ? cartItemRepository.findByCartIdAndProductId(cartId, productId)
                : cartItemRepository.findByCartIdAndProductIdAndVariantId(cartId, productId, variantId);
    }

    private ProductVariant validateProductAvailability(Product product, Long variantId, int requestedQuantity) {
        if (!Boolean.TRUE.equals(product.getActive())) throw new IllegalStateException("Товар больше недоступен");
        boolean requiresVariant = product.getCategory() != null && product.getCategory().getVariantType() != null
                && product.getCategory().getVariantType() != VariantType.NONE;
        if (!requiresVariant) {
            if (variantId != null) throw new IllegalArgumentException("Для этого товара вариант не выбирается");
            int available = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (available < requestedQuantity) throw new IllegalStateException("Недостаточно товара на складе. Доступно: " + available);
            return null;
        }
        if (variantId == null) throw new IllegalArgumentException("Выберите вариант товара");
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Вариант товара не найден"));
        if (!variant.getProduct().getId().equals(product.getId())) throw new IllegalArgumentException("Выбранный вариант не принадлежит товару");
        int available = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
        if (available < requestedQuantity) throw new IllegalStateException("Выбранного варианта недостаточно. Доступно: " + available);
        return variant;
    }
}
