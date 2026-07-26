package io.sentinelops.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Seeds a small catalog on startup if the table is empty. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final InventoryItemRepository items;

    public DataSeeder(InventoryItemRepository items) {
        this.items = items;
    }

    @Override
    public void run(String... args) {
        if (items.count() > 0) return;
        items.saveAll(List.of(
                new InventoryItem("SKU-LAPTOP", new BigDecimal("1299.00"), 500),
                new InventoryItem("SKU-PHONE",  new BigDecimal("899.00"),  500),
                new InventoryItem("SKU-HEADSET", new BigDecimal("149.00"), 500),
                new InventoryItem("SKU-KEYBOARD", new BigDecimal("79.00"), 500),
                new InventoryItem("SKU-MOUSE",   new BigDecimal("39.00"),  500)
        ));
    }
}
