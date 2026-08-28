package com.microfi.authentication;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class AgentDetails implements UserDetails {

    private final Agent agent;

    public Agent getAgent() {
        return agent;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_AGENT"));
    }

    @Override
    public String getPassword() {
        return agent.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return agent.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // PENDING_CEILING agents may still hold a session (they can log in and use the app; they just
    // can't collect — see AgentDirectoryService#verifyTransactionPin). SUSPENDED or DELETED locks
    // the account out of every request, which is what actually makes a suspend/delete take effect
    // on an already-issued token (JwtService#isTokenValid rechecks these on every call).
    @Override
    public boolean isAccountNonLocked() {
        return agent.getStatus() != AgentStatus.SUSPENDED && agent.getStatus() != AgentStatus.DELETED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return agent.getStatus() != AgentStatus.SUSPENDED && agent.getStatus() != AgentStatus.DELETED;
    }
}
