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
        return agent.getPinHash();
    }

    @Override
    public String getUsername() {
        return agent.getEmployeeCode();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return agent.getStatus() == AgentStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return agent.getStatus() == AgentStatus.ACTIVE;
    }
}
