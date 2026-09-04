package com.microfi.transactions.service;

import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.transactions.domain.OfjSession;
import com.microfi.transactions.repository.OfjSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfjClosingTimeExportJobTest {

    @Mock
    private BranchRepository branchRepository;
    @Mock
    private OfjSessionRepository ofjSessionRepository;
    @Mock
    private OfjService ofjService;

    private OfjClosingTimeExportJob job;

    private final UUID branchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        job = new OfjClosingTimeExportJob(branchRepository, ofjSessionRepository, ofjService);
    }

    /** Simulates "closeTime already passed" or "not yet" by picking a closeTime relative to right now in a fixed zone. */
    private Branch branchWithCloseTimeOffsetFromNow(long minutesFromNow, ZoneId zone) {
        LocalTime closeTime = ZonedDateTime.now(zone).plusMinutes(minutesFromNow).toLocalTime();
        return Branch.builder().id(branchId).code("BR1").name("Branch 1").closeTime(closeTime).timezone(zone.getId()).build();
    }

    @Test
    void exportsTodaysSessionForABranchPastItsClosingTime() {
        ZoneId zone = ZoneId.of("Africa/Douala");
        Branch branch = branchWithCloseTimeOffsetFromNow(-5, zone); // closeTime was 5 minutes ago — already past
        when(branchRepository.findByCloseTimeIsNotNullAndTimezoneIsNotNull()).thenReturn(List.of(branch));
        OfjSession session = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(LocalDate.now(zone)).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, LocalDate.now(zone))).thenReturn(Optional.of(session));

        job.exportBranchesPastClosingTime();

        verify(ofjService, times(1)).runScheduledClosingExport(session);
    }

    @Test
    void skipsABranchNotYetPastItsClosingTime() {
        ZoneId zone = ZoneId.of("Africa/Douala");
        Branch branch = branchWithCloseTimeOffsetFromNow(30, zone); // closeTime is 30 minutes from now — not yet
        when(branchRepository.findByCloseTimeIsNotNullAndTimezoneIsNotNull()).thenReturn(List.of(branch));

        job.exportBranchesPastClosingTime();

        verify(ofjService, never()).runScheduledClosingExport(any());
    }

    @Test
    void skipsABranchWithNoSessionYetToday() {
        ZoneId zone = ZoneId.of("Africa/Douala");
        Branch branch = branchWithCloseTimeOffsetFromNow(-5, zone);
        when(branchRepository.findByCloseTimeIsNotNullAndTimezoneIsNotNull()).thenReturn(List.of(branch));
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, LocalDate.now(zone))).thenReturn(Optional.empty());

        job.exportBranchesPastClosingTime();

        verify(ofjService, never()).runScheduledClosingExport(any());
    }

    @Test
    void skipsABranchWithAGarbageTimezoneString() {
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1")
                .closeTime(LocalTime.of(0, 0)).timezone("not-a-real-zone").build();
        when(branchRepository.findByCloseTimeIsNotNullAndTimezoneIsNotNull()).thenReturn(List.of(branch));

        job.exportBranchesPastClosingTime();

        verify(ofjService, never()).runScheduledClosingExport(any());
    }

    @Test
    void noOpWhenNoBranchesHaveClosingHoursConfigured() {
        when(branchRepository.findByCloseTimeIsNotNullAndTimezoneIsNotNull()).thenReturn(List.of());

        job.exportBranchesPastClosingTime();

        verify(ofjSessionRepository, never()).findByBranchIdAndBusinessDate(any(), any());
    }
}
