package com.shlyapoff.shop;

import org.junit.jupiter.api.Test;
import com.shlyapoff.shop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:shop;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"telegram.enabled=false",
		"telegram.bot-token=test-token",
		"telegram.admin-chat-id=1"
})
class ShopApplicationTests {

	@Autowired
	private ProductService productService;

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

}
