package com.microfi.savings.service;

import com.microfi.savings.ClientDetails;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.ClientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ClientDetailsServiceTest {

    @Mock
    private ClientProfileRepository clientProfileRepository;

    private ClientDetailsService clientDetailsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clientDetailsService = new ClientDetailsService(clientProfileRepository);
    }

    @Test
    void findByUsernameReturnsClientDetailsWhenFound() {
        ClientProfile client = ClientProfile.builder().login("jean.client").pinHash("hashed").build();
        when(clientProfileRepository.findByLogin("jean.client")).thenReturn(Optional.of(client));

        UserDetails result = clientDetailsService.findByUsername("jean.client");

        assertThat(result).isInstanceOf(ClientDetails.class);
        assertThat(result.getUsername()).isEqualTo("jean.client");
    }

    @Test
    void findByUsernameThrowsWhenNotFound() {
        when(clientProfileRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientDetailsService.findByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
