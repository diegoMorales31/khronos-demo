package com.moga.khronosdemo.security;

import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.entities.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class SecurityUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final Role role;
    private final boolean enabled;
    private final Instant credentialsChangedAt;
    // The owner's plan, read with the rest of the account on every request; null for anyone else
    private final Plan plan;

    public SecurityUser(Long id, String username, String password, Role role, boolean enabled,
                        Instant credentialsChangedAt) {
        this(id, username, password, role, enabled, credentialsChangedAt, null);
    }

    public SecurityUser(Long id, String username, String password, Role role, boolean enabled,
                        Instant credentialsChangedAt, Plan plan) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
        this.credentialsChangedAt = credentialsChangedAt;
        this.plan = plan;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    public Plan getPlan() {
        return plan;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
