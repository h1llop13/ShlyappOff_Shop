package com.shlyapoff.shop.integration;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Run the same regression scenarios against a fresh disposable PostgreSQL database.
 * A database name allowlist prevents accidentally pointing this suite at the shop database. */
@EnabledIfEnvironmentVariable(named = "SHOP_TEST_POSTGRES_URL",
        matches = "jdbc:postgresql://[^/]+/shlyapoff_test")
@SpringBootTest(properties = {
        "spring.datasource.url=${SHOP_TEST_POSTGRES_URL}",
        "spring.datasource.username=${SHOP_TEST_POSTGRES_USER:shop_test}",
        "spring.datasource.password=${SHOP_TEST_POSTGRES_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "telegram.enabled=false", "telegram.bot-token=test-token", "telegram.admin-chat-id=1",
        "app.admin.password=regression-test-password", "app.orders.reservation-cleanup-ms=3600000"
})
class PostgresProductionIntegrationTest extends ProductionRegressionIntegrationTest {
}
