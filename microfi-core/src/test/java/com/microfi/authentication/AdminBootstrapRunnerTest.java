package com.microfi.authentication;

import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.repository.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runner = new AdminBootstrapRunner(adminUserRepository, passwordEncoder);
    }

    private void setBootstrapCredentials(String login, String password) {
        ReflectionTestUtils.setField(runner, "bootstrapLogin", login);
        ReflectionTestUtils.setField(runner, "bootstrapPassword", password);
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() {
        setBootstrapCredentials("admin", "ChangeMe123!");
        when(adminUserRepository.existsByRole(AdminRole.ADMIN)).thenReturn(true);

        runner.run(applicationArguments);

        verify(adminUserRepository, never()).save(any(AdminUser.class));
    }

    @Test
    void bootstrapsFirstAdminWhenNoneExistsAndCredentialsProvided() {
        setBootstrapCredentials("admin", "ChangeMe123!");
        when(adminUserRepository.existsByRole(AdminRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("hashed");

        runner.run(applicationArguments);

        verify(adminUserRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getLogin().equals("admin")
                        && saved.getPasswordHash().equals("hashed")
                        && saved.getRole() == AdminRole.ADMIN
                        && saved.getBranchId() == null));
    }

    @Test
    void doesNothingWhenNoneExistsAndCredentialsMissing() {
        setBootstrapCredentials("", "");
        when(adminUserRepository.existsByRole(AdminRole.ADMIN)).thenReturn(false);

        runner.run(applicationArguments);

        verify(adminUserRepository, never()).save(any(AdminUser.class));
    }
}
