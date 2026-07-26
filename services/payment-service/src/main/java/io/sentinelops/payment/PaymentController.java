package io.sentinelops.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    public record AuthorizeRequest(@NotBlank String orderRef, @NotNull @Positive BigDecimal amount) { }
    public record CaptureRequest(@NotBlank String paymentId) { }
    public record PaymentView(String paymentId, String orderRef, BigDecimal amount, String status) { }

    @PostMapping("/authorize")
    public PaymentView authorize(@Valid @RequestBody AuthorizeRequest req) {
        Payment p = service.authorize(req.orderRef(), req.amount());
        return view(p);
    }

    @PostMapping("/capture")
    public PaymentView capture(@Valid @RequestBody CaptureRequest req) {
        Payment p = service.capture(req.paymentId());
        return view(p);
    }

    private PaymentView view(Payment p) {
        return new PaymentView(p.getId(), p.getOrderRef(), p.getAmount(), p.getStatus().name());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<Map<String, String>> onDeclined(PaymentDeclinedException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> onMissing(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
