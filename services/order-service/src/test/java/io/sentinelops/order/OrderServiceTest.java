package io.sentinelops.order;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sentinelops.order.client.InventoryClient;
import io.sentinelops.order.client.PaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orders;
    private InventoryClient inventory;
    private PaymentClient payment;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        inventory = mock(InventoryClient.class);
        payment = mock(PaymentClient.class);
        // Repository saves echo the entity back, like a real save would.
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new OrderService(orders, inventory, payment, new SimpleMeterRegistry());
    }

    @Test
    void completesCheckoutAndConfirmsBothSides() {
        when(inventory.reserve(eq("SKU-MOUSE"), eq(2)))
                .thenReturn(new InventoryClient.Reservation("res-1", "SKU-MOUSE", 2, new BigDecimal("39.00")));
        when(payment.authorize(anyString(), any()))
                .thenReturn(new PaymentClient.Payment("pay-1", "order", new BigDecimal("78.00"), "AUTHORIZED"));

        Order result = service.checkout("SKU-MOUSE", 2);

        assertThat(result.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(result.getAmount()).isEqualByComparingTo("78.00"); // 39.00 * 2
        verify(inventory).confirm("res-1");
        verify(payment).capture("pay-1");
        verify(inventory, never()).release(anyString());
    }

    @Test
    void releasesReservationWhenPaymentFails() {
        when(inventory.reserve(anyString(), anyInt()))
                .thenReturn(new InventoryClient.Reservation("res-9", "SKU-PHONE", 1, new BigDecimal("899.00")));
        when(payment.authorize(anyString(), any()))
                .thenThrow(new RuntimeException("payment declined"));

        assertThatThrownBy(() -> service.checkout("SKU-PHONE", 1))
                .isInstanceOf(CheckoutException.class);

        // Compensation: the held stock must be released.
        verify(inventory, times(1)).release("res-9");
        verify(payment, never()).capture(anyString());
    }
}
