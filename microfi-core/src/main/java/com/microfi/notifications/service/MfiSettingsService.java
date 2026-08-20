package com.microfi.notifications.service;

import com.microfi.notifications.domain.MfiSettings;
import com.microfi.notifications.repository.MfiSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Admin-configurable MFI name (BR-Notif-01) — {@link NotificationService} was hardcoding
 * "MICROFI" before this existed, which would have been wrong for any other tenant. Falls back to
 * {@code mfi.name} (env-configurable, defaults to "MICROFI") only until an admin sets a real value.
 */
@Service
@RequiredArgsConstructor
public class MfiSettingsService {

    private final MfiSettingsRepository mfiSettingsRepository;

    @Value("${mfi.name:MICROFI}")
    private String bootstrapDefaultName;

    public String getName() {
        return mfiSettingsRepository.findById(MfiSettings.SINGLETON_ID)
                .map(MfiSettings::getName)
                .orElse(bootstrapDefaultName);
    }

    public String updateName(String name) {
        MfiSettings settings = mfiSettingsRepository.findById(MfiSettings.SINGLETON_ID)
                .orElseGet(() -> MfiSettings.builder().id(MfiSettings.SINGLETON_ID).build());
        settings.setName(name);
        return mfiSettingsRepository.save(settings).getName();
    }
}
