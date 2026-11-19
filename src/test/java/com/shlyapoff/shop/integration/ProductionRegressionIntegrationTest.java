package com.shlyapoff.shop.integration;

import com.shlyapoff.shop.model.*;
import com.shlyapoff.shop.repository.*;
import com.shlyapoff.shop.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:production-regression;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "telegram.enabled=false",
        "telegram.bot-token=test-token", "telegram.admin-chat-id=1",
        "app.admin.password=regression-test-password",
        "app.orders.reservation-cleanup-ms=3600000"
})
@AutoConfigureMockMvc
class ProductionRegressionIntegrationTest {
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired CustomerService customers;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customerRepository;
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired PromoCodeService promos;
    @Autowired ProductVariantRepository variants;
    @Autowired OrderRepository orderRepository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;

    @Test
    void mergingGuestCartPreservesDistinctAndSharedItems() {
        Product shared = product(20);
        Product guestOnly = product(20);
        String known = UUID.randomUUID().toString();
        String guest = UUID.randomUUID().toString();
        long telegramId = 901001L;
        carts.addToCart(known, telegramId, shared.getId(), null, 1);
        carts.addToCart(guest, shared.getId(), 2);
        carts.addToCart(guest, guestOnly.getId(), 4);
        carts.bindTelegramCart(guest, telegramId);

        Cart merged = carts.getCart(guest, telegramId).orElseThrow();
        assertThat(merged.getItems()).hasSize(2);
        assertThat(merged.getItems()).extracting(CartItem::getQuantity).containsExactlyInAnyOrder(3, 4);
        assertThat(carts.getCartBySessionId(guest)).isEmpty();
    }

    @Test
    void twoCheckoutsCannotBothBuyLastItemFromCachedStock() throws Exception {
        Product product = product(1);
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        carts.addToCart(first, product.getId(), 1);
        carts.addToCart(second, product.getId(), 1);
        CyclicBarrier readTogether = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(() -> checkoutWithCachedStock(first, product.getId(), readTogether));
            var two = workers.submit(() -> checkoutWithCachedStock(second, product.getId(), readTogether));
            int successful = (one.get(20, TimeUnit.SECONDS) ? 1 : 0) + (two.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successful).as("Only one order may consume the last unit").isEqualTo(1);
        }
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
    }

    private boolean checkoutWithCachedStock(String session, Long productId, CyclicBarrier barrier) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
                entityManager.find(Product.class, productId); // Both requests saw stock=1 before acquiring the lock.
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception e) { throw new IllegalStateException(e); }
                orders.createOrderFromCart(session, "Покупатель", "+79990000000", "Самовывоз", null, null, null);
            });
            return true;
        } catch (RuntimeException expectedConflict) { return false; }
    }

    @Test
    void staleCustomerCannotSpendTheSameBonusesTwice() {
        Customer customer = new Customer();
        customer.setTelegramUserId(901002L);
        customer.setBonusBalance(new BigDecimal("100"));
        customer = customerRepository.saveAndFlush(customer);
        Customer first = customerRepository.findById(customer.getId()).orElseThrow();
        Customer stale = customerRepository.findById(customer.getId()).orElseThrow();
        customers.spendBonuses(first, new BigDecimal("80"));
        assertThatThrownBy(() -> customers.spendBonuses(stale, new BigDecimal("80")))
                .isInstanceOf(RuntimeException.class);
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getBonusBalance()).isEqualByComparingTo("20");
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanEditProductWithDetachedCategory() throws Exception {
        Category category = category();
        Brand brand = new Brand();
        brand.setName(UUID.randomUUID().toString());
        brand = brands.saveAndFlush(brand);
        Product product = product(5);
        product.setCategory(category);
        product.setBrand(brand);
        product = products.saveAndFlush(product);
        mvc.perform(multipart("/admin/product/edit/" + product.getId())
                        .file(new MockMultipartFile("imageFile", new byte[0])).with(csrf())
                        .param("name", "Обновлённый товар").param("price", "50.00").param("stockQuantity", "5")
                        .param("category_id", category.getId().toString()).param("brand_id", brand.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(products.findById(product.getId()).orElseThrow().getName()).isEqualTo("Обновлённый товар");
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void populatedAdminPagesAndFormsRender() throws Exception {
        Product product = product(5);
        String session = UUID.randomUUID().toString();
        carts.addToCart(session, product.getId(), 1);
        orders.createOrderFromCart(session, "Тест", "+79990000000", "Самовывоз", null, null, null);
        for (String url : new String[]{"/admin", "/admin/orders", "/admin/customers", "/admin/loyalty",
                "/admin/categories", "/admin/brands", "/admin/promotions", "/admin/promo-codes",
                "/admin/promotions/create", "/admin/promo-codes/create", "/admin/dashboard"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void invalidJsonIsClientError() throws Exception {
        mvc.perform(post("/api/cart/bind").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/category/create").param("name", "not authorized"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveProductCannotBeOpenedFromOldLink() throws Exception {
        Product product = product(5);
        product.setActive(false);
        products.saveAndFlush(product);
        mvc.perform(get("/product/" + product.getId())).andExpect(status().is3xxRedirection());
    }

    @Test
    void invalidPromoLimitsAreRejectedBeforeDatabaseWrite() {
        PromoCode promo = new PromoCode();
        promo.setCode("REGRESSION");
        promo.setDiscountType(DiscountType.FIXED_AMOUNT);
        promo.setDiscountValue(BigDecimal.TEN);
        promo.setUsageLimit(0);
        assertThatThrownBy(() -> promos.save(promo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuredAdminPasswordActuallyAuthenticates() throws Exception {
        mvc.perform(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin()
                        .user("admin").password("regression-test-password"))
                .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated());
        mvc.perform(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin()
                        .user("admin").password("wrong-password"))
                .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated());
    }

    @Test
    void cancellingOrderRestoresInventoryAndBonusesOnlyOnce() {
        Product product = product(4);
        Customer customer = new Customer();
        customer.setTelegramUserId(901003L);
        customer.setBonusBalance(new BigDecimal("50"));
        customer = customerRepository.saveAndFlush(customer);
        String session = UUID.randomUUID().toString();
        carts.addToCart(session, customer.getTelegramUserId(), product.getId(), null, 1);
        Order order = orders.createOrderFromCart(session, "Покупатель", "+79990000000", "Самовывоз", null,
                customer.getTelegramUserId(), null, true);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("50");
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getBonusBalance()).isZero();
        orders.updateStatus(order.getId(), "CANCELLED");
        orders.updateStatus(order.getId(), "CANCELLED");
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getBonusBalance()).isEqualByComparingTo("50");
        assertThatThrownBy(() -> orders.updateStatus(order.getId(), "COMPLETED")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void variantSelectionIsRequiredAndCancelledOrderRestoresVariantStock() {
        Category category = category();
        category.setVariantType(VariantType.FLAVOR);
        categories.saveAndFlush(category);
        Product product = product(0);
        product.setCategory(category);
        products.saveAndFlush(product);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setValue("Вариант");
        variant.setStockQuantity(6);
        variant = variants.saveAndFlush(variant);
        String session = UUID.randomUUID().toString();
        assertThatThrownBy(() -> carts.addToCart(session, product.getId(), 1)).isInstanceOf(IllegalArgumentException.class);
        carts.addToCart(session, product.getId(), variant.getId(), 2);
        Order order = orders.createOrderFromCart(session, "Покупатель", "+79990000000", "Самовывоз", null, null, null);
        assertThat(variants.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
        orders.updateStatus(order.getId(), "CANCELLED");
        orders.updateStatus(order.getId(), "CANCELLED");
        assertThat(variants.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(6);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    void checkoutRejectsInvalidInputAndRepeatedSubmitDoesNotCreateSecondOrder() throws Exception {
        Product product = product(2);
        var session = new org.springframework.mock.web.MockHttpSession();
        carts.addToCart(session.getId(), product.getId(), 1);
        long before = orderRepository.count();
        mvc.perform(post("/checkout").session(session).with(csrf())
                        .param("customerName", "Покупатель").param("deliveryType", "Самовывоз"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("orderDto", "phone"));
        assertThat(orderRepository.count()).isEqualTo(before);
        mvc.perform(post("/checkout").session(session).with(csrf())
                        .param("customerName", "Покупатель").param("phone", "+79990000000")
                        .param("deliveryType", "Самовывоз"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/success?orderId=*"));
        mvc.perform(post("/checkout").session(session).with(csrf())
                        .param("customerName", "Покупатель").param("phone", "+79990000000")
                        .param("deliveryType", "Самовывоз"))
                .andExpect(redirectedUrl("/cart"));
        assertThat(orderRepository.count()).isEqualTo(before + 1);
    }

    private Category category() {
        Category category = new Category();
        category.setName(UUID.randomUUID().toString());
        category.setVariantType(VariantType.NONE);
        return categories.saveAndFlush(category);
    }

    private Product product(int stock) {
        Product product = new Product();
        product.setName("Regression " + UUID.randomUUID());
        product.setPrice(new BigDecimal("100.00"));
        product.setStockQuantity(stock);
        return products.saveAndFlush(product);
    }
}
