package io.sentinelops.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryItemRepository items;
    private final ReservationRepository reservations;

    /** Artificial processing delay (ms) to make the service tunably "slow" for
        cascading-failure demos. Real network delay comes from Chaos Mesh. */
    @Value("${app.delay-ms:0}")
    private long delayMs;

    public InventoryService(InventoryItemRepository items, ReservationRepository reservations) {
        this.items = items;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public InventoryItem get(String sku) {
        return items.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("unknown sku: " + sku));
    }

    @Transactional
    public Reservation reserve(String sku, int quantity) {
        applyDelay();
        InventoryItem item = items.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("unknown sku: " + sku));
        item.reserve(quantity);
        items.save(item);
        Reservation reservation = new Reservation(UUID.randomUUID().toString(), sku, quantity);
        return reservations.save(reservation);
    }

    @Transactional
    public void confirm(String reservationId) {
        Reservation r = load(reservationId);
        if (r.getStatus() != Reservation.Status.PENDING) return; // idempotent
        InventoryItem item = get(r.getSku());
        item.confirm(r.getQuantity());
        items.save(item);
        r.markConfirmed();
        reservations.save(r);
    }

    @Transactional
    public void release(String reservationId) {
        Reservation r = load(reservationId);
        if (r.getStatus() != Reservation.Status.PENDING) return; // idempotent
        InventoryItem item = get(r.getSku());
        item.release(r.getQuantity());
        items.save(item);
        r.markReleased();
        reservations.save(r);
    }

    private Reservation load(String reservationId) {
        return reservations.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("unknown reservation: " + reservationId));
    }

    private void applyDelay() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
