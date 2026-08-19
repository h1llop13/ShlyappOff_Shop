package com.shlyapoff.shop;

import org.junit.jupiter.api.Test;
import com.shlyapoff.shop.service.ProductService;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.OrderStatusHistory;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.repository.OrderStatusHistoryRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.PromotionRepository;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.Promotion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderStatusHistoryRepository orderStatusHistoryRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PromotionRepository promotionRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@WithMockUser(authorities = "ROLE_ADMIN")
	void inventoryAdminPageRendersJournalAndCsvActions() throws Exception {
		mockMvc.perform(get("/admin/inventory"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Журнал движений")))
				.andExpect(content().string(containsString("Экспорт CSV")))
				.andExpect(content().string(containsString("Мало товара")));
	}

	@Test
	@WithMockUser(authorities = "ROLE_ADMIN")
	void inventoryCsvExportUsesUtf8Template() throws Exception {
		mockMvc.perform(get("/admin/inventory/export"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=inventory.csv"))
				.andExpect(content().contentType("text/csv;charset=UTF-8"))
				.andExpect(content().string(containsString("product_id,name,description,price")));
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
		mockMvc.perform(get("/admin/audit"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Журнал действий администраторов")));
		mockMvc.perform(get("/admin/promo-codes"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Промокоды")));
		mockMvc.perform(get("/admin/promotions/create"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Условия акции")))
				.andExpect(content().string(containsString("Промокод")));
	}

	@Test
	void profileContainsRepeatOrderAction() throws Exception {
		mockMvc.perform(get("/profile"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Повторить заказ")));
	}

	@Test
	@Transactional
	void personalizationAndPromotionPagesRender() throws Exception {
		Product product = new Product();
		product.setName("Товар для рекомендаций");
		product.setPrice(new BigDecimal("250.00"));
		product.setStockQuantity(3);
		product = productRepository.saveAndFlush(product);

		Promotion promotion = new Promotion();
		promotion.setTitle("Тестовая акция");
		promotion.setDescription("Проверка промо-страницы");
		promotion.setActive(true);
		promotion = promotionRepository.saveAndFlush(promotion);

		mockMvc.perform(get("/product/" + product.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Характеристики")));
		mockMvc.perform(get("/promotions"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Тестовая акция")));
		mockMvc.perform(get("/promotions/" + promotion.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Условия акции")));
	}

	@Test
	@Transactional
	@WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
	void adminOrdersRenderLifecycleActionsAndHistory() throws Exception {
		Order order = new Order();
		order.setCustomerName("Тестовый покупатель");
		order.setDeliveryType("Самовывоз");
		order.setSubtotalAmount(new BigDecimal("100.00"));
		order.setTotalAmount(new BigDecimal("100.00"));
		order = orderRepository.saveAndFlush(order);

		OrderStatusHistory history = new OrderStatusHistory();
		history.setOrder(order);
		history.setNewStatus(OrderStatus.NEW);
		history.setChangedAt(LocalDateTime.now());
		history.setChangedBy("customer:web");
		orderStatusHistoryRepository.saveAndFlush(history);

		mockMvc.perform(get("/admin/orders"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Переходов: 1")))
				.andExpect(content().string(containsString("Подтверждён")))
				.andExpect(content().string(containsString("Причина отмены")));
	}

	@Test
	void telegramLoaderIsServedLocallyAndLoadsSdkAsynchronously() throws Exception {
		mockMvc.perform(get("/js/telegram-loader.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("script.async = true")))
				.andExpect(content().string(containsString(
						"script.src = '/js/telegram-web-app.js'"
				)));
	}

	@Test
	void containerHealthEndpointIsPublicForDockerHealthcheck() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"status\":\"UP\"")));
	}

}
