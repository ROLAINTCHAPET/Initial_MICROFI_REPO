package com.microfi.savings;

import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.domain.ClientStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Client-facing self-service principal (UC-20/21/22). Deliberately not locked out by an expired
 * {@link com.microfi.savings.domain.AccessToken} — an expired booklet token stays read-only
 * consultable, it doesn't block login; only the local mirror row's own {@link ClientStatus}
 * (set by a Back-Office operator) can lock the account out.
 */
@RequiredArgsConstructor
public class ClientDetails implements UserDetails {

    private final ClientProfile client;

    public ClientProfile getClient() {
        return client;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CLIENT"));
    }

    @Override
    public String getPassword() {
        return client.getPinHash();
    }

    @Override
    public String getUsername() {
        return client.getLogin();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return client.getStatus() == ClientStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return client.getStatus() == ClientStatus.ACTIVE;
    }
}
