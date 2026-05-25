package com.wms.ai.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.inventory.InventoryService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the README "worker no longer IDLE (raced)" / oversell guard: a single
 * conditional UPDATE makes concurrent reserves race-safe at the DB level.
 *
 * <p>Uses {@code @SpringBootTest} (not {@code @DataJpaTest}) on purpose — we need
 * the real connection pool and per-call transactions that commit, so worker
 * threads contend on the row across connections rather than sharing one
 * roll-backed test transaction.
 */
@SpringBootTest
class InventoryServiceConcurrencyTest {

    @Autowired
    InventoryService service;

    @Autowired
    StockRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void concurrentReservesNeverOversell() throws InterruptedException {
        int initialStock = 50;
        int threads = 100; // twice the stock, so contention is guaranteed
        repository.save(new StockEntity("SKU-HOT", initialStock, "ZONE-1"));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.await();
                    if (service.reserve("SKU-HOT", 1)) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.countDown(); // release all threads at once
        assertThat(done.await(30, TimeUnit.SECONDS))
                .as("all worker threads finished")
                .isTrue();
        pool.shutdown();

        int remaining = service.getStock("SKU-HOT").orElseThrow().quantity();
        assertThat(successes.get())
                .as("never reserve more than was in stock")
                .isEqualTo(initialStock);
        assertThat(remaining).as("stock fully drained, never negative").isZero();
        assertThat(successes.get() + remaining)
                .as("conservation: reserved + remaining == initial")
                .isEqualTo(initialStock);
    }
}
