package mx.artemisa.artemisaapi.platform;

import mx.artemisa.artemisaapi.identity.TenantOnboardingService;
import mx.artemisa.artemisaapi.identity.api.TenantOnboardingRequest;
import mx.artemisa.artemisaapi.platform.api.PlatformProvisioningResponse;
import mx.artemisa.artemisaapi.platform.api.ProvisionTenantRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PlatformProvisioningService {
    private final TenantOnboardingService onboarding;
    private final JdbcTemplate jdbc;
    public PlatformProvisioningService(TenantOnboardingService onboarding, JdbcTemplate jdbc) {
        this.onboarding = onboarding; this.jdbc = jdbc;
    }

    @Transactional
    public PlatformProvisioningResponse provision(ProvisionTenantRequest request, UUID platformUserId) {
        var created = onboarding.onboard(new TenantOnboardingRequest(request.tenantName(),
                request.tenantSlug(), request.branchName(), request.timezone(), request.adminFullName(),
                request.adminEmail(), request.initialPassword()));
        jdbc.update("UPDATE tenants SET created_by_platform_user_id=? WHERE id=?",
                platformUserId, created.tenantId());
        return new PlatformProvisioningResponse(created.tenantId(), created.branchId(),
                created.userId(), created.membershipId(), created.adminEmail());
    }
}
