package com.wms.ai.order.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.order.Order;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.order.Priority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the dev-profile {@code CommandLineRunner} seeds sample orders on
 * startup. Runs in the {@code dev} profile with its own isolated in-memory DB so
 * it neither depends on nor pollutes the default-profile tests' shared database.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:orderseedtest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class OrderSeedDataTest {

    @Autowired
    OrderService service;

    @Autowired
    OrderSeedData seedData;

    @Test
    void seedsPendingOrdersAcrossPrioritiesOnStartup() {
        var all = service.listAll();

        assertThat(all)
                .extracting(Order::customer)
                .contains("Acme", "Globex", "Initech", "Umbrella");
        assertThat(all)
                .extracting(Order::priority)
                .contains(Priority.URGENT, Priority.HIGH, Priority.NORMAL, Priority.LOW);
        assertThat(all)
                .extracting(Order::status)
                .containsOnly(OrderStatus.PENDING);
    }

    @Test
    void reRunningTheSeederDoesNotDuplicate() {
        int afterStartup = service.listAll().size();

        seedData.run(); // simulate a restart

        assertThat(service.listAll()).hasSize(afterStartup);
    }
}
