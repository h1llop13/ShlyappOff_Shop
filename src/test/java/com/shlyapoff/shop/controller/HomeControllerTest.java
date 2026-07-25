package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.service.BrandService;
import com.shlyapoff.shop.service.CartService;
import com.shlyapoff.shop.service.CategoryService;
import com.shlyapoff.shop.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeControllerTest {

    private static final String SESSION_ID = "cart-session";

    private ProductService productService;
    private CartService cartService;
    private HttpServletRequest request;
    private HomeController controller;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        cartService = mock(CartService.class);
        controller = new HomeController(
                productService,
                mock(CategoryService.class),
                mock(BrandService.class),
                cartService
        );

        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(session);
    }

    @Test
    void returnsCartFeedbackAfterAddingProduct() {
        Product product = new Product();
        product.setName("Тестовый товар");
        CartItem cartItem = new CartItem();
        cartItem.setQuantity(2);
        Cart cart = new Cart();
        cart.getItems().add(cartItem);
        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.of(cart));
        when(productService.findById(5L)).thenReturn(Optional.of(product));

        var response = controller.addToCartAsync(5L, null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new HomeController.CartAddResponse(
                true, "Тестовый товар", 2, null
        ));
        verify(cartService).addToCart(SESSION_ID, 5L, null, 1);
    }

    @Test
    void returnsValidationErrorInsteadOfRedirectForAsyncRequest() {
        doThrow(new IllegalArgumentException("Выберите вариант товара"))
                .when(cartService).addToCart(SESSION_ID, 5L, null, 1);

        var response = controller.addToCartAsync(5L, null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new HomeController.CartAddResponse(
                false, null, 0, "Выберите вариант товара"
        ));
    }
}
