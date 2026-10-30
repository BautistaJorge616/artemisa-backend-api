package mx.artemisa.artemisaapi.platform;

import mx.artemisa.artemisaapi.identity.TenantOnboardingService;
import mx.artemisa.artemisaapi.identity.api.TenantOnboardingResponse;
import mx.artemisa.artemisaapi.platform.api.ProvisionTenantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlatformProvisioningServiceTest {
    @Test
    void provisionsTenantAndAuditsPlatformActor() {
        TenantOnboardingService onboarding = mock(TenantOnboardingService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID tenant = UUID.randomUUID(), branch = UUID.randomUUID(), user = UUID.randomUUID();
        UUID membership = UUID.randomUUID(), platformUser = UUID.randomUUID();
        when(onboarding.onboard(any())).thenReturn(new TenantOnboardingResponse(
                tenant, branch, user, membership, "admin@example.test"));
        var service = new PlatformProvisioningService(onboarding, jdbc);
        var request = new ProvisionTenantRequest("Estancia", "estancia", "Centro",
                "America/Mexico_City", "Admin", "admin@example.test", "initial-password-123");

        var result = service.provision(request, platformUser);

        assertEquals(tenant, result.tenantId());
        assertEquals(branch, result.branchId());
        verify(jdbc).update("UPDATE tenants SET created_by_platform_user_id=? WHERE id=?", platformUser, tenant);
    }
}
