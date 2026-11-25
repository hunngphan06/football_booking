package com.example.footballbooking.security;

import com.example.footballbooking.dto.AccountData.SignedIn;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private final SignedIn account;

    public AccountPrincipal(String email, String passwordHash, String role, String name, Long customerId) {
        super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        account = new SignedIn(role, name, customerId);
    }

    public SignedIn account() {
        return account;
    }

    public Long customerId() {
        return account.customerId();
    }
}
