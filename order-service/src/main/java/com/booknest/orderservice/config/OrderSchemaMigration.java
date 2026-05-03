package com.booknest.orderservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSchemaMigration {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateOrderStatusColumns() {
        alterColumn("ALTER TABLE orders MODIFY COLUMN order_status VARCHAR(32) NOT NULL", "orders.order_status");
        alterColumn("ALTER TABLE order_status_logs MODIFY COLUMN status VARCHAR(32) NOT NULL", "order_status_logs.status");
    }

    private void alterColumn(String sql, String label) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Schema migration applied for {}", label);
        } catch (Exception ex) {
            log.warn("Schema migration skipped for {}: {}", label, ex.getMessage());
        }
    }
}
