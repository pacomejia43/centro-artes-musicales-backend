package com.centroartesmusicales.backend.security;

import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public class SecurityUser implements UserDetails {

    private final Usuario usuario;

    public SecurityUser(Usuario usuario) {
        this.usuario = usuario;
    }

    public Long getId() {
        return usuario.getId();
    }

    public String getNombre() {
        return usuario.getNombre();
    }

    public Role getRole() {
        return usuario.getRole();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRole().name()));
    }

    @Override
    public String getPassword() {
        return usuario.getPassword();
    }

    @Override
    public String getUsername() {
        return usuario.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return usuario.isEnabled();
    }
}
