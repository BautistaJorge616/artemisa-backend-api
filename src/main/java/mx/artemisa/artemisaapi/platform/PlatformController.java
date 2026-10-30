package mx.artemisa.artemisaapi.platform;

import jakarta.validation.Valid;
import mx.artemisa.artemisaapi.platform.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
    private final PlatformAuthenticationService authentication;
    private final PlatformProvisioningService provisioning;
    public PlatformController(PlatformAuthenticationService authentication, PlatformProvisioningService provisioning) {
        this.authentication=authentication; this.provisioning=provisioning;
    }
    @PostMapping("/auth/login")
    public PlatformTokenResponse login(@Valid @RequestBody PlatformLoginRequest request) {
        return authentication.login(request);
    }
    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformProvisioningResponse provision(@Valid @RequestBody ProvisionTenantRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return provisioning.provision(request, UUID.fromString(jwt.getSubject()));
    }
}
