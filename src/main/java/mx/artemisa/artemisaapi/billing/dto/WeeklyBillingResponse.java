package mx.artemisa.artemisaapi.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WeeklyBillingResponse(
        UUID accountId, UUID studentId, LocalDate weekStart, LocalDate weekEnd,
        String currency, String status, BigDecimal charges, BigDecimal paid,
        BigDecimal balance, List<Charge> items) {
    public record Charge(String type, LocalDate serviceDate, String description,
                         BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) { }
}
