package com.microfi.authentication;

import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Creates the very first ADMIN account from environment-provided credentials, solving the
 * "who creates the first admin" bootstrap problem without a permanently-live unauthenticated
 * endpoint. Runs once per boot; a no-op once any ADMIN already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.login:}")
    private String bootstrapLogin;

    @Value("${admin.bootstrap.password:}")
    private String bootstrapPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.existsByRole(AdminRole.ADMIN)) {
            return;
        }
        if (bootstrapLogin.isBlank() || bootstrapPassword.isBlank()) {
            log.warn("No ADMIN account exists yet, and ADMIN_BOOTSTRAP_LOGIN / ADMIN_BOOTSTRAP_PASSWORD "
                    + "are not set. The back office cannot be accessed until an ADMIN account is created.");
            return;
        }

        AdminUser admin = AdminUser.builder()
                .id(UUID.randomUUID())
                .login(bootstrapLogin)
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .role(AdminRole.ADMIN)
                .build();
        adminUserRepository.save(admin);
        log.info("Bootstrapped initial ADMIN account '{}'", bootstrapLogin);
    }
}
