package com.shlyapoff.shop;

import org.junit.jupiter.api.Test;
import com.shlyapoff.shop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

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
