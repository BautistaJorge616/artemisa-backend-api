package mx.artemisa.artemisaapi.platform.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProvisionTenantRequest(
        @NotBlank @Size(max = 160) String tenantName,
        @NotBlank @Size(max = 80) String tenantSlug,
        @NotBlank @Size(max = 160) String branchName,
        @NotBlank @Size(max = 80) String timezone,
        @NotBlank @Size(max = 160) String adminFullName,
        @NotBlank @Email @Size(max = 254) String adminEmail,
        @NotBlank @Size(min = 12, max = 72) String initialPassword) { }
