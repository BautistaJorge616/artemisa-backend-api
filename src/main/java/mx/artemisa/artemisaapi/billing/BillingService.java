package mx.artemisa.artemisaapi.billing;

import mx.artemisa.artemisaapi.audit.AuditService;
import mx.artemisa.artemisaapi.billing.dto.RecordPaymentRequest;
import mx.artemisa.artemisaapi.billing.dto.WeeklyBillingResponse;
import mx.artemisa.artemisaapi.packageplan.OvertimeCalculator;
import mx.artemisa.artemisaapi.security.BranchAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {
    private final JdbcTemplate jdbc;
    private final BranchAuthorizationService auth;
    private final AuditService audit;

    public BillingService(JdbcTemplate jdbc, BranchAuthorizationService auth, AuditService audit) {
        this.jdbc = jdbc; this.auth = auth; this.audit = audit;
    }

    @Transactional
    public int calculateBranchWeek(UUID branchId, LocalDate weekStart, Jwt jwt) {
        UUID tenantId = auth.requireManagementAccess(branchId, jwt);
        requireMonday(weekStart);
        List<AccountSeed> seeds = jdbc.query("""
            SELECT s.id student_id, a.schedule_package_id package_id, p.package_code,
                   p.version, p.name, p.weekly_price, p.currency,
                   p.contracted_minutes_per_day, p.overtime_tolerance_minutes,
                   p.overtime_hourly_rate
            FROM students s
            JOIN student_package_assignments a ON a.tenant_id=s.tenant_id
              AND a.branch_id=s.branch_id AND a.student_id=s.id
            JOIN schedule_packages p ON p.tenant_id=a.tenant_id AND p.branch_id=a.branch_id
              AND p.id=a.schedule_package_id
            WHERE s.tenant_id=? AND s.branch_id=? AND s.status='ACTIVE'
              AND a.effective_from <= ? AND COALESCE(a.effective_to, DATE '9999-12-31') >= ?
              AND p.status='ACTIVE' AND p.effective_from <= ?
              AND COALESCE(p.effective_to, DATE '9999-12-31') >= ?
            ORDER BY s.id
            """, (rs, n) -> new AccountSeed(rs.getObject("student_id", UUID.class),
                rs.getObject("package_id", UUID.class), rs.getString("package_code"),
                rs.getInt("version"), rs.getString("name"), rs.getBigDecimal("weekly_price"),
                rs.getString("currency"), rs.getInt("contracted_minutes_per_day"),
                rs.getInt("overtime_tolerance_minutes"), rs.getBigDecimal("overtime_hourly_rate")),
            tenantId, branchId, weekStart, weekStart, weekStart, weekStart);

        for (AccountSeed seed : seeds) {
            if (seed.weeklyPrice == null || seed.weeklyPrice.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El paquete " + seed.code + " no tiene un precio semanal válido. Actualízalo antes de calcular la cobranza.");
            }
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO weekly_accounts(id,tenant_id,branch_id,student_id,package_id,
                  week_start,week_end,currency,package_code,package_version,package_name,
                  weekly_price_snapshot,contracted_minutes_per_day_snapshot,
                  overtime_tolerance_minutes_snapshot,overtime_hourly_rate_snapshot)
                VALUES(?,?,?,?,?,?,?, ?,?,?,?,?,?,?,?) ON CONFLICT(tenant_id,branch_id,student_id,week_start) DO NOTHING
                """, id, tenantId, branchId, seed.studentId, seed.packageId, weekStart,
                weekStart.plusDays(6), seed.currency, seed.code, seed.version, seed.name,
                seed.weeklyPrice, seed.contractedMinutes, seed.tolerance, seed.hourlyRate);
            AccountRef account = jdbc.query("SELECT id,status FROM weekly_accounts WHERE tenant_id=? AND branch_id=? AND student_id=? AND week_start=? FOR UPDATE",
                rs -> rs.next() ? new AccountRef(rs.getObject(1, UUID.class), rs.getString(2)) : null,
                tenantId, branchId, seed.studentId, weekStart);
            if (account == null || !"OPEN".equals(account.status)) continue;
            jdbc.update("DELETE FROM weekly_charges WHERE tenant_id=? AND weekly_account_id=? AND charge_type IN ('WEEKLY_PACKAGE','OVERTIME')", tenantId, account.id);
            addCharge(tenantId, account.id, "WEEKLY_PACKAGE", null,
                "Paquete semanal " + seed.code + " v" + seed.version,
                BigDecimal.ONE, seed.weeklyPrice, seed.weeklyPrice);
            List<DailyPresence> days = jdbc.query("""
                SELECT attendance_date,
                  MIN(event_at) FILTER (WHERE event_type='CHECK_IN') AS first_in,
                  MAX(event_at) FILTER (WHERE event_type='CHECK_OUT') AS last_out
                FROM attendance_events
                WHERE tenant_id=? AND branch_id=? AND student_id=?
                  AND attendance_date BETWEEN ? AND ?
                GROUP BY attendance_date ORDER BY attendance_date
                """, (rs,n)->new DailyPresence(rs.getObject("attendance_date",LocalDate.class),
                    rs.getObject("first_in", java.time.OffsetDateTime.class),
                    rs.getObject("last_out", java.time.OffsetDateTime.class)),
                tenantId, branchId, seed.studentId, weekStart, weekStart.plusDays(6));
            for (DailyPresence day : days) {
                if (day.in == null || day.out == null || !day.out.isAfter(day.in)) continue;
                long minutes = ChronoUnit.SECONDS.between(day.in.toInstant(), day.out.toInstant()) / 60;
                OvertimeCalculator.Result extra = OvertimeCalculator.calculate(minutes,
                    seed.contractedMinutes, seed.tolerance, seed.hourlyRate);
                if (extra.billableHours() > 0) {
                    BigDecimal qty = BigDecimal.valueOf(extra.billableHours());
                    addCharge(tenantId, account.id, "OVERTIME", day.date,
                        "Tiempo extra del " + day.date, qty, seed.hourlyRate,
                        qty.multiply(seed.hourlyRate).setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        return seeds.size();
    }

    @Transactional(readOnly = true)
    public WeeklyBillingResponse getStudentWeek(UUID branchId, UUID studentId, LocalDate weekStart, Jwt jwt) {
        UUID tenantId = auth.requireManagementAccess(branchId, jwt); requireMonday(weekStart);
        AccountRef account = jdbc.query("SELECT id,status FROM weekly_accounts WHERE tenant_id=? AND branch_id=? AND student_id=? AND week_start=?",
            rs->rs.next()?new AccountRef(rs.getObject(1,UUID.class),rs.getString(2)):null, tenantId,branchId,studentId,weekStart);
        if(account==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No existe cuenta semanal calculada.");
        return response(tenantId, account.id, studentId, weekStart, account.status);
    }

    @Transactional
    public WeeklyBillingResponse finalizeWeek(UUID branchId, UUID studentId, LocalDate weekStart, Jwt jwt) {
        UUID tenantId=auth.requireManagementAccess(branchId,jwt); requireMonday(weekStart);
        int changed=jdbc.update("UPDATE weekly_accounts SET status='FINALIZED', finalized_at=now(), updated_at=now() WHERE tenant_id=? AND branch_id=? AND student_id=? AND week_start=? AND status='OPEN'",tenantId,branchId,studentId,weekStart);
        if(changed==0) {
            Boolean exists=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM weekly_accounts WHERE tenant_id=? AND branch_id=? AND student_id=? AND week_start=?)",Boolean.class,tenantId,branchId,studentId,weekStart);
            if(!Boolean.TRUE.equals(exists)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No existe cuenta semanal.");
        }
        UUID actor=auth.requireMembershipId(jwt);
        audit.record(tenantId,branchId,actor,"WEEKLY_ACCOUNT_FINALIZED","WEEKLY_ACCOUNT",getAccountId(tenantId,branchId,studentId,weekStart));
        return getStudentWeek(branchId,studentId,weekStart,jwt);
    }

    @Transactional
    public WeeklyBillingResponse recordPayment(UUID branchId, UUID studentId, LocalDate weekStart, RecordPaymentRequest req, Jwt jwt) {
        UUID tenantId=auth.requireManagementAccess(branchId,jwt); requireMonday(weekStart);
        UUID actor=auth.requireMembershipId(jwt);
        AccountRef account=jdbc.query("SELECT id,status FROM weekly_accounts WHERE tenant_id=? AND branch_id=? AND student_id=? AND week_start=? FOR UPDATE",
            rs->rs.next()?new AccountRef(rs.getObject(1,UUID.class),rs.getString(2)):null,tenantId,branchId,studentId,weekStart);
        if(account==null || !"FINALIZED".equals(account.status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"La cuenta debe existir y estar finalizada antes de registrar pagos.");
        BigDecimal total=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM weekly_charges WHERE tenant_id=? AND weekly_account_id=?",BigDecimal.class,tenantId,account.id);
        BigDecimal paid=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_allocations WHERE tenant_id=? AND weekly_account_id=?",BigDecimal.class,tenantId,account.id);
        if(req.amount().compareTo(total.subtract(paid))>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"El pago excede el saldo pendiente de la semana.");
        String method=req.paymentMethod().strip().toUpperCase();
        if(!List.of("CASH","CARD","BANK_TRANSFER","OTHER").contains(method)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"paymentMethod debe ser CASH, CARD, BANK_TRANSFER u OTHER.");
        String currency=jdbc.queryForObject("SELECT currency FROM weekly_accounts WHERE tenant_id=? AND id=?",String.class,tenantId,account.id);
        UUID payment=UUID.randomUUID();
        jdbc.update("INSERT INTO payments(id,tenant_id,branch_id,student_id,amount,currency,payment_method,reference,received_by_membership_id) VALUES(?,?,?,?,?,?,?,?,?)",payment,tenantId,branchId,studentId,req.amount(),currency,method,req.reference(),actor);
        jdbc.update("INSERT INTO payment_allocations(id,tenant_id,payment_id,weekly_account_id,amount) VALUES(?,?,?,?,?)",UUID.randomUUID(),tenantId,payment,account.id,req.amount());
        audit.record(tenantId,branchId,actor,"PAYMENT_RECORDED","PAYMENT",payment);
        return response(tenantId,account.id,studentId,weekStart,account.status);
    }

    @Transactional(readOnly=true)
    public BranchSummary summary(UUID branchId, LocalDate weekStart, Jwt jwt) {
        UUID tenantId=auth.requireManagementAccess(branchId,jwt); requireMonday(weekStart);
        return jdbc.query("""
          SELECT COUNT(*) accounts,
            COALESCE(SUM(c.total),0) charged,
            COALESCE(SUM(p.paid),0) paid,
            COALESCE(SUM(c.total-p.paid),0) balance
          FROM weekly_accounts a
          LEFT JOIN LATERAL (SELECT COALESCE(SUM(amount),0) total FROM weekly_charges WHERE tenant_id=a.tenant_id AND weekly_account_id=a.id) c ON true
          LEFT JOIN LATERAL (SELECT COALESCE(SUM(amount),0) paid FROM payment_allocations WHERE tenant_id=a.tenant_id AND weekly_account_id=a.id) p ON true
          WHERE a.tenant_id=? AND a.branch_id=? AND a.week_start=?
          """,rs->{rs.next(); return new BranchSummary(weekStart,rs.getLong("accounts"),rs.getBigDecimal("charged"),rs.getBigDecimal("paid"),rs.getBigDecimal("balance"));},tenantId,branchId,weekStart);
    }

    private WeeklyBillingResponse response(UUID tenant,UUID account,UUID student,LocalDate week,String status) {
        List<WeeklyBillingResponse.Charge> items=jdbc.query("SELECT charge_type,service_date,description,quantity,unit_price,amount FROM weekly_charges WHERE tenant_id=? AND weekly_account_id=? ORDER BY service_date NULLS FIRST,created_at",(rs,n)->new WeeklyBillingResponse.Charge(rs.getString(1),rs.getObject(2,LocalDate.class),rs.getString(3),rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getBigDecimal(6)),tenant,account);
        BigDecimal charges=items.stream().map(WeeklyBillingResponse.Charge::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal paid=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_allocations WHERE tenant_id=? AND weekly_account_id=?",BigDecimal.class,tenant,account);
        String currency=jdbc.queryForObject("SELECT currency FROM weekly_accounts WHERE tenant_id=? AND id=?",String.class,tenant,account);
        return new WeeklyBillingResponse(account,student,week,week.plusDays(6),currency,status,charges,paid,charges.subtract(paid),items);
    }
    private void addCharge(UUID tenant,UUID account,String type,LocalDate date,String description,BigDecimal qty,BigDecimal unit,BigDecimal amount){
        jdbc.update("INSERT INTO weekly_charges(id,tenant_id,weekly_account_id,charge_type,service_date,description,quantity,unit_price,amount) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,account,type,date,description,qty,unit,amount);
    }
    private UUID getAccountId(UUID tenant,UUID branch,UUID student,LocalDate week){return jdbc.queryForObject("SELECT id FROM weekly_accounts WHERE tenant_id=? AND branch_id=? AND student_id=? AND week_start=?",UUID.class,tenant,branch,student,week);}
    private static void requireMonday(LocalDate date){if(date==null||date.getDayOfWeek().getValue()!=1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"weekStart debe ser lunes (YYYY-MM-DD).");}
    private record AccountSeed(UUID studentId,UUID packageId,String code,int version,String name,BigDecimal weeklyPrice,String currency,int contractedMinutes,int tolerance,BigDecimal hourlyRate){}
    private record AccountRef(UUID id,String status){}
    private record DailyPresence(LocalDate date,java.time.OffsetDateTime in,java.time.OffsetDateTime out){}
    public record BranchSummary(LocalDate weekStart,long accounts,BigDecimal charged,BigDecimal paid,BigDecimal balance){}
}
