package com.microfi.notifications.service;

import com.microfi.notifications.domain.MfiSettings;
import com.microfi.notifications.repository.MfiSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MfiSettingsServiceTest {

    @Mock
    private MfiSettingsRepository mfiSettingsRepository;

    private MfiSettingsService mfiSettingsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mfiSettingsService = new MfiSettingsService(mfiSettingsRepository);
        ReflectionTestUtils.setField(mfiSettingsService, "bootstrapDefaultName", "MICROFI");
    }

    @Test
    void getNameFallsBackToBootstrapDefaultWhenNeverConfigured() {
        when(mfiSettingsRepository.findById(MfiSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThat(mfiSettingsService.getName()).isEqualTo("MICROFI");
    }

    @Test
    void getNameReturnsAdminConfiguredValue() {
        when(mfiSettingsRepository.findById(MfiSettings.SINGLETON_ID))
                .thenReturn(Optional.of(MfiSettings.builder().id(MfiSettings.SINGLETON_ID).name("Coopec Alpha").build()));

        assertThat(mfiSettingsService.getName()).isEqualTo("Coopec Alpha");
    }

    @Test
    void updateNameCreatesRowWhenNoneExistsYet() {
        when(mfiSettingsRepository.findById(MfiSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(mfiSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = mfiSettingsService.updateName("Coopec Beta");

        assertThat(result).isEqualTo("Coopec Beta");
    }

    @Test
    void updateNameOverwritesExistingRow() {
        MfiSettings existing = MfiSettings.builder().id(MfiSettings.SINGLETON_ID).name("Old Name").build();
        when(mfiSettingsRepository.findById(MfiSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(mfiSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = mfiSettingsService.updateName("New Name");

        assertThat(result).isEqualTo("New Name");
        assertThat(existing.getName()).isEqualTo("New Name");
    }
}
