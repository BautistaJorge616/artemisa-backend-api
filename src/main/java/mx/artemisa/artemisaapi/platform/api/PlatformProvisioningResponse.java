package mx.artemisa.artemisaapi.platform.api;

import java.util.UUID;

public record PlatformProvisioningResponse(UUID tenantId, UUID branchId, UUID adminUserId,
                                           UUID membershipId, String adminEmail) { }
