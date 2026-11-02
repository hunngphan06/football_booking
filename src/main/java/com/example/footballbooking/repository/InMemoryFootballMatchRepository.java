package com.example.footballbooking.repository;

import com.example.footballbooking.model.FootballMatch;
import com.example.footballbooking.model.TicketType;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Du lieu hu cau de demo API, khong phai lich thi dau that. */
@Repository
public class InMemoryFootballMatchRepository implements FootballMatchRepository {
    private final List<FootballMatch> matches = List.of(
        new FootballMatch(1L, "FC Sao Mai", "FC Song Xanh", "San Anh Duong",
            Instant.parse("2026-12-05T12:00:00Z"),
            "Tran giao huu mau. Mo cua truoc gio thi dau 60 phut.", "BTC Anh Duong",
            List.of(new TicketType(101L, "Khan dai A", new BigDecimal("200000"), 100),
                    new TicketType(102L, "VIP", new BigDecimal("500000"), 20))),
        new FootballMatch(2L, "FC Binh Minh", "FC Gio Nam", "San Hoa Binh",
            Instant.parse("2026-12-06T11:00:00Z"),
            "Tran dau mau phuc vu thuc hanh API.", "BTC Hoa Binh",
            List.of(new TicketType(201L, "Khan dai B", new BigDecimal("150000"), 150)))
    );

    @Override
    public List<FootballMatch> findAll() {
        return matches;
    }

    @Override
    public Optional<FootballMatch> findById(Long id) {
        return matches.stream().filter(match -> match.getId().equals(id)).findFirst();
    }
}
