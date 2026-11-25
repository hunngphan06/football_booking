package com.example.footballbooking.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcClient db;

    public AccountRepository(JdbcClient db) {
        this.db = db;
    }

    public record Account(String email, String passwordHash, String role, String name, Long customerId) {}

    public Optional<Account> find(String email) {
        return db.sql("SELECT email, password_hash, role, display_name, customer_id FROM booking_accounts WHERE email = ?")
            .param(email).query((rs, row) -> new Account(rs.getString("email"), rs.getString("password_hash"),
                rs.getString("role"), rs.getString("display_name"), rs.getObject("customer_id", Long.class)))
            .optional();
    }

    public void insert(Account account) {
        db.sql("""
            INSERT INTO booking_accounts (email, password_hash, role, display_name, customer_id)
            VALUES (:email, :hash, :role, :name, :customerId)
            """).param("email", account.email()).param("hash", account.passwordHash())
            .param("role", account.role()).param("name", account.name()).param("customerId", account.customerId())
            .update();
    }
}
