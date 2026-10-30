package mx.artemisa.artemisaapi.branch;

import java.util.UUID;

public record BranchStaffResponse(UUID userId, UUID membershipId, String fullName,
                                  String email, String membershipStatus,
                                  String roleCode, UUID branchId) { }
