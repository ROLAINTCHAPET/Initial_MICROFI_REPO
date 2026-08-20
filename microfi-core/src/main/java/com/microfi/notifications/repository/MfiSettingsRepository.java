package com.microfi.notifications.repository;

import com.microfi.notifications.domain.MfiSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MfiSettingsRepository extends JpaRepository<MfiSettings, UUID> {
}
