package com.microfi.authentication.repository;

import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    Optional<AdminUser> findByLogin(String login);
    boolean existsByLogin(String login);
    boolean existsByRole(AdminRole role);
}
