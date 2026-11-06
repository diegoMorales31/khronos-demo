package com.moga.khronosdemo.config;

import com.moga.khronosdemo.entities.Role;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${app.admin.username:}") String username,
                          @Value("${app.admin.password:}") String password,
                          @Value("${app.admin.email:}") String email) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.PLATFORM_ADMIN)) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(email)) {
            log.warn("No PLATFORM_ADMIN exists; set APP_ADMIN_USERNAME, APP_ADMIN_PASSWORD and APP_ADMIN_EMAIL to create one");
            return;
        }
        User admin = new User(username, passwordEncoder.encode(password), "Platform", "Admin", null, email);
        admin.setRole(Role.PLATFORM_ADMIN);
        userRepository.save(admin);
        log.info("Created PLATFORM_ADMIN '{}'", username);
    }
}
