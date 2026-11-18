package com.moga.khronosdemo.config;

import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Only a platform admin can create users through the API, so the first one is created at startup
 * from app.admin.username, app.admin.password and app.admin.email. It runs on every start but never
 * changes an account that already exists, so the settings can stay in place.
 */
@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt only uses the first 72 bytes of a password
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AdminProperties properties, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createAdminIfMissing();
    }

    public void createAdminIfMissing() {
        if (!properties.isComplete()) {
            if (!properties.isEmpty()) {
                log.warn("app.admin is only partly set; set the username, password and email to create the first admin");
            }
            return;
        }

        String username = properties.username();
        if (userRepository.existsByUsername(username)) {
            log.info("Platform admin bootstrap skipped: user '{}' already exists and is left unchanged", username);
            return;
        }
        if (userRepository.existsByEmail(properties.email())) {
            log.warn("Platform admin bootstrap skipped: the email for '{}' is already used by another account", username);
            return;
        }

        String password = properties.password();
        if (password.length() < MIN_PASSWORD_LENGTH || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH
                    + " characters and at most " + MAX_PASSWORD_BYTES + " bytes long");
        }

        User admin = new User(username, passwordEncoder.encode(password), "Platform", "Admin", null, properties.email());
        admin.setRole(Role.PLATFORM_ADMIN);
        try {
            userRepository.save(admin);
            log.info("Created platform admin '{}'", username);
        } catch (DataIntegrityViolationException ex) {
            // Another instance starting at the same time created it first
            log.info("Platform admin bootstrap skipped: user '{}' was created concurrently", username);
        }
    }
}
