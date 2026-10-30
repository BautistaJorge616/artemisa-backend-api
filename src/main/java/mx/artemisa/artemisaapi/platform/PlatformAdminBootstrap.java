package mx.artemisa.artemisaapi.platform;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Locale;
import java.util.UUID;

/** One-time, explicitly enabled bootstrap for the first Artemisa platform operator. */
@Configuration
public class PlatformAdminBootstrap {
    @Bean
    @ConditionalOnProperty(name = "app.platform.bootstrap-admin.enabled", havingValue = "true")
    ApplicationRunner bootstrapPlatformAdmin(JdbcTemplate jdbc, PasswordEncoder encoder,
            org.springframework.core.env.Environment env) {
        return args -> {
            String email = required(env, "app.platform.bootstrap-admin.email").toLowerCase(Locale.ROOT);
            String name = required(env, "app.platform.bootstrap-admin.full-name");
            String password = required(env, "app.platform.bootstrap-admin.password");
            if (password.length() < 16) throw new IllegalStateException("Bootstrap password must be at least 16 characters.");
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM platform_users WHERE lower(email)=lower(?)", Integer.class, email);
            if (exists != null && exists > 0) return;
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO platform_users(id,email,full_name,password_hash,status) VALUES (?,?,?,?, 'ACTIVE')",
                    id, email, name, encoder.encode(password));
            jdbc.update("INSERT INTO platform_role_assignments(platform_user_id,role_code) VALUES (?, 'PLATFORM_ADMIN')", id);
        };
    }
    private static String required(org.springframework.core.env.Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required platform bootstrap setting: " + key);
        return value;
    }
}
