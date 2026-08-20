package com.microfi.registration.repository;

import com.microfi.registration.domain.RegistrationApplication;
import com.microfi.registration.domain.RegistrationApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrationApplicationRepository extends JpaRepository<RegistrationApplication, UUID> {

    List<RegistrationApplication> findByBranchId(UUID branchId);

    List<RegistrationApplication> findByStatus(RegistrationApplicationStatus status);

    List<RegistrationApplication> findByBranchIdAndStatus(UUID branchId, RegistrationApplicationStatus status);

    // Used at submission time to reject a duplicate against another pending/approved application
    // (not just against already-provisioned accounts) — excludes REJECTED so a resubmission after
    // rejection with the same identifiers isn't blocked by its own predecessor.
    boolean existsByLoginAndStatusNot(String login, RegistrationApplicationStatus status);

    boolean existsByPhoneAndStatusNot(String phone, RegistrationApplicationStatus status);

    boolean existsByEmailAndStatusNot(String email, RegistrationApplicationStatus status);

    boolean existsByNationalIdNumberAndStatusNot(String nationalIdNumber, RegistrationApplicationStatus status);

    boolean existsByTaxIdNumberAndStatusNot(String taxIdNumber, RegistrationApplicationStatus status);
}
