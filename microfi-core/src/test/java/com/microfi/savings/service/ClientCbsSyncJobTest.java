package com.microfi.savings.service;

import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.shared.dto.MiddlewareMemberLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientCbsSyncJobTest {

    @Mock
    private ClientProfileRepository clientProfileRepository;
    @Mock
    private CbsClientService cbsClientService;

    private ClientCbsSyncJob job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        job = new ClientCbsSyncJob(clientProfileRepository, cbsClientService);
    }

    @Test
    void overwritesLocalFieldsFromAMatchingCbsMember() {
        ClientProfile client = ClientProfile.builder().id(UUID.randomUUID()).mfiMemberNo("M001")
                .fullName("Placeholder Name").email(null).phone(null).build();
        when(clientProfileRepository.findAll()).thenReturn(List.of(client));
        when(cbsClientService.getMember("M001")).thenReturn(Mono.just(
                new MiddlewareMemberLookup(true, "M001", "Jean Client (CBS)", "jean@cbs.example", "+237600000001")));
        when(clientProfileRepository.save(any(ClientProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        job.syncClientsAgainstCbs();

        ArgumentCaptor<ClientProfile> captor = ArgumentCaptor.forClass(ClientProfile.class);
        verify(clientProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("Jean Client (CBS)");
        assertThat(captor.getValue().getEmail()).isEqualTo("jean@cbs.example");
        assertThat(captor.getValue().getPhone()).isEqualTo("+237600000001");
        assertThat(captor.getValue().getCbsSyncedAt()).isNotNull();
    }

    @Test
    void leavesLocalEmailAndPhoneAloneWhenTheCbsHasNoneOnFile() {
        ClientProfile client = ClientProfile.builder().id(UUID.randomUUID()).mfiMemberNo("M001")
                .fullName("Placeholder Name").email("kept@example.com").phone("+237600000099").build();
        when(clientProfileRepository.findAll()).thenReturn(List.of(client));
        when(cbsClientService.getMember("M001")).thenReturn(Mono.just(new MiddlewareMemberLookup(true, "M001", "Jean Client (CBS)", null, null)));
        when(clientProfileRepository.save(any(ClientProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        job.syncClientsAgainstCbs();

        ArgumentCaptor<ClientProfile> captor = ArgumentCaptor.forClass(ClientProfile.class);
        verify(clientProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("kept@example.com");
        assertThat(captor.getValue().getPhone()).isEqualTo("+237600000099");
    }

    @Test
    void leavesTheClientUntouchedWhenNoMatchingCbsMemberExists() {
        ClientProfile client = ClientProfile.builder().id(UUID.randomUUID()).mfiMemberNo("UNKNOWN").fullName("Someone").build();
        when(clientProfileRepository.findAll()).thenReturn(List.of(client));
        when(cbsClientService.getMember("UNKNOWN")).thenReturn(Mono.just(new MiddlewareMemberLookup(false, "UNKNOWN", null, null, null)));

        job.syncClientsAgainstCbs();

        verify(clientProfileRepository, never()).save(any());
    }

    @Test
    void skipsAClientWithoutFailingTheWholeRunWhenTheCbsCallErrors() {
        ClientProfile failing = ClientProfile.builder().id(UUID.randomUUID()).mfiMemberNo("BROKEN").fullName("Broken").build();
        ClientProfile ok = ClientProfile.builder().id(UUID.randomUUID()).mfiMemberNo("M002").fullName("Placeholder").build();
        when(clientProfileRepository.findAll()).thenReturn(List.of(failing, ok));
        when(cbsClientService.getMember("BROKEN")).thenReturn(Mono.error(new RuntimeException("middleware down")));
        when(cbsClientService.getMember("M002")).thenReturn(Mono.just(new MiddlewareMemberLookup(true, "M002", "Real Name", "e@x.com", "+237600000002")));
        when(clientProfileRepository.save(any(ClientProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        job.syncClientsAgainstCbs();

        ArgumentCaptor<ClientProfile> captor = ArgumentCaptor.forClass(ClientProfile.class);
        verify(clientProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getMfiMemberNo()).isEqualTo("M002");
    }

    @Test
    void doesNothingWhenThereAreNoClientsAtAll() {
        when(clientProfileRepository.findAll()).thenReturn(List.of());

        job.syncClientsAgainstCbs();

        verify(cbsClientService, never()).getMember(any());
    }
}
