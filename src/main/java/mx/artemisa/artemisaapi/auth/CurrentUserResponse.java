package mx.artemisa.artemisaapi.auth;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String fullName,
        UUID tenantId,
        UUID membershipId,
        List<String> roles,
        List<String> branchIds,
        List<String> groupIds
) {
}
