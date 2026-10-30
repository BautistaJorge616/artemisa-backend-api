package mx.artemisa.artemisaapi.branch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class BranchStaffService {
    private final JdbcTemplate jdbc;
    public BranchStaffService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<BranchStaffResponse> list(UUID tenantId, UUID branchId) {
        return jdbc.query("""
                SELECT DISTINCT u.id AS user_id, tm.id AS membership_id, u.full_name,
                       u.email, tm.status AS membership_status, r.code AS role_code,
                       mra.branch_id
                FROM tenant_memberships tm
                JOIN app_users u ON u.id = tm.app_user_id
                JOIN membership_role_assignments mra
                  ON mra.tenant_id=tm.tenant_id AND mra.tenant_membership_id=tm.id
                JOIN roles r ON r.code=mra.role_code
                WHERE tm.tenant_id=? AND tm.status IN ('ACTIVE','INVITED')
                  AND (mra.branch_id=? OR (mra.branch_id IS NULL AND r.code='TENANT_ADMIN'))
                ORDER BY u.full_name, r.code
                """, (rs, n) -> new BranchStaffResponse(
                rs.getObject("user_id", UUID.class), rs.getObject("membership_id", UUID.class),
                rs.getString("full_name"), rs.getString("email"), rs.getString("membership_status"),
                rs.getString("role_code"), rs.getObject("branch_id", UUID.class)), tenantId, branchId);
    }
}
