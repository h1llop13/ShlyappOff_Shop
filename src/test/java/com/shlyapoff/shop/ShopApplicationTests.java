package com.shlyapoff.shop;

import org.junit.jupiter.api.Test;
import com.shlyapoff.shop.service.ProductService;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.ProductRepository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:shop;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"telegram.enabled=false",
		"telegram.bot-token=test-token",
		"telegram.admin-chat-id=1"
})
@AutoConfigureMockMvc
class ShopApplicationTests {

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductRepository productRepository;

	@Test
	@Transactional
	void catalogSlicesAndPriceSortPreserveFilters() {
		for (int index = 1; index <= 14; index++) {
			Product product = new Product();
			product.setName("CatalogSliceFixture " + index);
			product.setPrice(BigDecimal.valueOf(index));
			product.setStockQuantity(index == 1 ? 0 : 5);
			productRepository.save(product);
		}
		productRepository.flush();

		var first = productService.findWithFilters("CatalogSliceFixture", null, null,
				BigDecimal.valueOf(2), null, true, "price_asc", 0, 12);
		var second = productService.findWithFilters("CatalogSliceFixture", null, null,
				BigDecimal.valueOf(2), null, true, "price_asc", 1, 12);

		org.assertj.core.api.Assertions.assertThat(first.getContent()).hasSize(12);
		org.assertj.core.api.Assertions.assertThat(first.hasNext()).isTrue();
		org.assertj.core.api.Assertions.assertThat(first.getContent().getFirst().getPrice())
				.isEqualByComparingTo("2.00");
		org.assertj.core.api.Assertions.assertThat(second.getContent()).hasSize(1);
		org.assertj.core.api.Assertions.assertThat(second.hasNext()).isFalse();
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void productCardQueriesExecuteForEveryFilterCombination() {
		productService.findWithFilters(null, null, null, 0, 12);
		productService.findWithFilters("vape", null, null, 0, 12);
		productService.findWithFilters(null, 1L, null, 0, 12);
		productService.findWithFilters(null, null, 1L, 0, 12);
		productService.findWithFilters("vape", 1L, null, 0, 12);
		productService.findWithFilters("vape", null, 1L, 0, 12);
		productService.findWithFilters(null, 1L, 1L, 0, 12);
		productService.findWithFilters("vape", 1L, 1L, 0, 12);
	}

	@Test
	void homeDoesNotBlockOnTelegramSdkAndAllowsTelegramWebEmbedding() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("X-Frame-Options"))
				.andExpect(header().string(
						"Content-Security-Policy",
						"frame-ancestors 'self' https://web.telegram.org https://*.telegram.org"
				))
				.andExpect(content().string(containsString("<script src=\"/js/telegram-loader.js\"></script>")))
				.andExpect(content().string(not(containsString(
						"<script src=\"https://telegram.org/js/telegram-web-app.js"
				))));
	}

	@Test
	void catalogLoadsWithoutSearchParameters() throws Exception {
		mockMvc.perform(get("/catalog"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Каталог")));
	}

	@Test
	void emptyCartShowsHelpfulEmptyState() throws Exception {
		mockMvc.perform(get("/cart"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Корзина пуста")))
				.andExpect(content().string(containsString("Перейти в каталог")));
	}

	@Test
	void storefrontLoadsSkeletonAssetsAndHasNoFavoritesUi() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/css/loading-states.css")))
				.andExpect(content().string(containsString("/js/loading-states.js")))
				.andExpect(content().string(not(containsString("favorite-btn"))));
	}

	@Test
	@WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
	void adminDashboardAndPromoCodesRender() throws Exception {
		mockMvc.perform(get("/admin/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Продажи")));
		mockMvc.perform(get("/admin/promo-codes"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Промокоды")));
	}

	@Test
	void telegramLoaderLoadsOfficialSdkAsynchronously() throws Exception {
		mockMvc.perform(get("/js/telegram-loader.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("script.async = true")))
				.andExpect(content().string(containsString(
						"script.src = 'https://telegram.org/js/telegram-web-app.js?61'"
				)));
	}

	@Test
	void containerHealthEndpointIsPublicForDockerHealthcheck() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"status\":\"UP\"")));
	}

    @Test
    void publicPlaceholderAndSessionProbeDoNotRedirectToLogin() throws Exception {
        mockMvc.perform(get("/img/placeholder.svg")).andExpect(status().isOk());
        mockMvc.perform(get("/api/cart/session"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.bound").value(false));
    }

    @Test
    void signedTelegramCartSurvivesNewSessionAndSupportsCheckout() throws Exception {
        Product product = new Product();
        product.setName("Mini App recovery fixture");
        product.setPrice(BigDecimal.valueOf(150));
        product.setStockQuantity(5);
        productRepository.saveAndFlush(product);
        var first = new org.springframework.mock.web.MockHttpSession();
        String initData = signedTelegramData(987654L);
        var bound = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cart/bind")
                .session(first).contentType("application/json").content("{\"initData\":\"" + initData + "\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.changed").value(true))
                .andReturn();
        String csrf = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bound.getResponse().getContentAsString())
                .get("csrfToken").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/cart/add")
                .session(first).header("X-Requested-With", "XMLHttpRequest")
                .param("productId", product.getId().toString()).param("_csrf", csrf))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.itemCount").value(1));
        var reopened = new org.springframework.mock.web.MockHttpSession();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cart/bind")
                .session(reopened).contentType("application/json").content("{\"initData\":\"" + initData + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cart/session").session(reopened))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.bound").value(true));
        mockMvc.perform(get("/cart").session(reopened)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Mini App recovery fixture")));
        mockMvc.perform(get("/checkout").session(reopened)).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cart/bind")
                .session(reopened).contentType("application/json").content("{\"initData\":\"" + initData + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.changed").value(false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cart/bind")
                .session(reopened).contentType("application/json").content("{\"initData\":\"" + signedTelegramData(777L) + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedTelegramDataReturnsUnauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/cart/bind")
                .contentType("application/json").content("{\"initData\":\"user=%ZZ&hash=abc\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String signedTelegramData(long userId) throws Exception {
        String date = Long.toString(java.time.Instant.now().getEpochSecond());
        String user = "{\"id\":" + userId + ",\"first_name\":\"Test\"}";
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("WebAppData".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secret = mac.doFinal("test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
        String hash = java.util.HexFormat.of().formatHex(mac.doFinal(("auth_date=" + date + "\nuser=" + user)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return "auth_date=" + date + "&user=" + java.net.URLEncoder.encode(user, java.nio.charset.StandardCharsets.UTF_8) + "&hash=" + hash;
    }

}
