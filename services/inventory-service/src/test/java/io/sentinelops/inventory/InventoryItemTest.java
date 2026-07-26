package io.sentinelops.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void reserveMovesUnitsFromAvailableToReserved() {
        InventoryItem item = new InventoryItem("SKU-KEYBOARD", new BigDecimal("79.00"), 10);
        item.reserve(3);
        assertThat(item.getAvailable()).isEqualTo(7);
        assertThat(item.getReserved()).isEqualTo(3);
    }

    @Test
    void confirmReducesReservedWithoutTouchingAvailable() {
        InventoryItem item = new InventoryItem("SKU-KEYBOARD", new BigDecimal("79.00"), 10);
        item.reserve(4);
        item.confirm(4);
        assertThat(item.getAvailable()).isEqualTo(6);
        assertThat(item.getReserved()).isZero();
    }

    @Test
    void releaseReturnsUnitsToAvailable() {
        InventoryItem item = new InventoryItem("SKU-KEYBOARD", new BigDecimal("79.00"), 10);
        item.reserve(4);
        item.release(4);
        assertThat(item.getAvailable()).isEqualTo(10);
        assertThat(item.getReserved()).isZero();
    }

    @Test
    void overReservingThrows() {
        InventoryItem item = new InventoryItem("SKU-KEYBOARD", new BigDecimal("79.00"), 2);
        assertThatThrownBy(() -> item.reserve(5))
                .isInstanceOf(InsufficientStockException.class);
    }
}
