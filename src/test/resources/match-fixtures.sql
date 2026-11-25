DELETE FROM ticket_types;
DELETE FROM events;
INSERT INTO events (id, title, home_team, away_team, stadium, start_time, organizer)
VALUES (1, 'FC Sao Mai vs FC Song Xanh', 'FC Sao Mai', 'FC Song Xanh', 'San Anh Duong', '2026-12-05 12:00:00+00', 'BTC A'),
       (2, 'FC Binh Minh vs FC Gio Nam', 'FC Binh Minh', 'FC Gio Nam', 'San Hoa Binh', '2026-12-06 11:00:00+00', 'BTC B');
INSERT INTO ticket_types (match_id, name, price, total_quantity)
VALUES (1, 'Khan dai A', 200000, 100);
ALTER TABLE events ALTER COLUMN id RESTART WITH 1000;
