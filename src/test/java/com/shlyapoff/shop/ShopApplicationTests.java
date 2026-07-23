package com.shlyapoff.shop;

import org.junit.jupiter.api.Test;
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

	@Test
	void contextLoads() {
	}

}
