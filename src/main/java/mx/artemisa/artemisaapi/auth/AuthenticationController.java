package mx.artemisa.artemisaapi.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final JdbcTemplate jdbcTemplate;

    public AuthenticationController(AuthenticationService authenticationService, JdbcTemplate jdbcTemplate) {
        this.authenticationService = authenticationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUserResponse(
                UUID.fromString(jwt.getSubject()),
                jdbcTemplate.queryForObject("SELECT full_name FROM app_users WHERE id = ?",
                        String.class, UUID.fromString(jwt.getSubject())),
                UUID.fromString(jwt.getClaimAsString("tenant_id")),
                UUID.fromString(jwt.getClaimAsString("membership_id")),
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsStringList("branch_ids"),
                jwt.getClaimAsStringList("group_ids")
        );
    }
}
