package mx.artemisa.artemisaapi.platform;

import mx.artemisa.artemisaapi.platform.api.PlatformLoginRequest;
import mx.artemisa.artemisaapi.platform.api.PlatformTokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PlatformAuthenticationService {
    private static final long TOKEN_TTL_SECONDS = 900;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;

    public PlatformAuthenticationService(JdbcTemplate jdbc, PasswordEncoder passwords, JwtEncoder encoder) {
        this.jdbc = jdbc; this.passwords = passwords; this.encoder = encoder;
    }

    public PlatformTokenResponse login(PlatformLoginRequest request) {
        var rows = jdbc.query("""
                SELECT id, password_hash, status FROM platform_users WHERE lower(email)=?
                """, (rs, n) -> new Account(rs.getObject("id", UUID.class),
                rs.getString("password_hash"), rs.getString("status")),
                request.email().trim().toLowerCase(Locale.ROOT));
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().status())
                || !passwords.matches(request.password(), rows.getFirst().hash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
        }
        Account account = rows.getFirst();
        List<String> roles = jdbc.queryForList("""
                SELECT role_code FROM platform_role_assignments WHERE platform_user_id=? ORDER BY role_code
                """, String.class, account.id());
        Instant now = Instant.now();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("artemisa-api").subject(account.id().toString())
                        .issuedAt(now).expiresAt(now.plusSeconds(TOKEN_TTL_SECONDS))
                        .claim("principal_type", "PLATFORM").claim("roles", roles).build())).getTokenValue();
        return new PlatformTokenResponse(token, "Bearer", TOKEN_TTL_SECONDS);
    }

    private record Account(UUID id, String hash, String status) { }
}
