package com.example.footballbooking.repository;

import com.example.footballbooking.model.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
}
