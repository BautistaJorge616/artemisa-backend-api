package mx.artemisa.artemisaapi.billing;

import jakarta.validation.Valid;
import mx.artemisa.artemisaapi.billing.dto.RecordPaymentRequest;
import mx.artemisa.artemisaapi.billing.dto.WeeklyBillingResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches/{branchId}/billing")
public class BillingController {
    private final BillingService service;
    public BillingController(BillingService service) { this.service = service; }

    @PostMapping("/weeks/{weekStart}/calculate")
    public Map<String,Object> calculate(@PathVariable UUID branchId,
            @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal Jwt jwt) {
        return Map.of("weekStart",weekStart,"accountsConsidered",service.calculateBranchWeek(branchId,weekStart,jwt));
    }
    @GetMapping("/summary")
    public BillingService.BranchSummary summary(@PathVariable UUID branchId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal Jwt jwt) { return service.summary(branchId,weekStart,jwt); }
    @GetMapping("/students/{studentId}/weeks/{weekStart}")
    public WeeklyBillingResponse get(@PathVariable UUID branchId,@PathVariable UUID studentId,
            @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal Jwt jwt) { return service.getStudentWeek(branchId,studentId,weekStart,jwt); }
    @PostMapping("/students/{studentId}/weeks/{weekStart}/finalize")
    public WeeklyBillingResponse finalizeWeek(@PathVariable UUID branchId,@PathVariable UUID studentId,
            @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal Jwt jwt) { return service.finalizeWeek(branchId,studentId,weekStart,jwt); }
    @PostMapping("/students/{studentId}/weeks/{weekStart}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public WeeklyBillingResponse payment(@PathVariable UUID branchId,@PathVariable UUID studentId,
            @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @Valid @RequestBody RecordPaymentRequest request,@AuthenticationPrincipal Jwt jwt) {
        return service.recordPayment(branchId,studentId,weekStart,request,jwt);
    }
}
