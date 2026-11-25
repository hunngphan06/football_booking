INSERT INTO events (id, title, category, home_team, away_team, stadium, start_time, description, organizer)
VALUES
 (1, 'Hà Nội vs Hải Phòng', 'SPORTS', 'Hà Nội', 'Hải Phòng', 'Hàng Đẫy', '2026-12-08 19:00:00+07',
  'Mở cửa lúc 18:00. Có bãi giữ xe máy tại cổng số 2. Trẻ em dưới 6 tuổi đi cùng người lớn được miễn vé.', 'BTC giải demo'),
 (2, 'Nam Định vs Sông Lam Nghệ An', 'SPORTS', 'Nam Định', 'Sông Lam Nghệ An', 'Thiên Trường', '2026-12-12 18:00:00+07',
  'Mở cửa lúc 17:00. Khán giả cần mang giấy tờ tùy thân.', 'BTC giải demo'),
 (3, 'Bình Dương vs Đà Nẵng', 'SPORTS', 'Bình Dương', 'Đà Nẵng', 'Gò Đậu', '2026-12-13 17:00:00+07',
  'Mở cửa lúc 16:00. Không mang chai thủy tinh vào sân.', 'BTC giải demo'),
 (4, 'Chuyến tàu mùa đông', 'CINEMA', NULL, NULL, 'Rạp Ánh Sao, phòng 2', '2026-12-12 15:00:00+07',
  'Suất chiếu buổi chiều.', 'Rạp Ánh Sao'),
 (5, 'Chuyến tàu mùa đông', 'CINEMA', NULL, NULL, 'Rạp Ánh Sao, phòng 2', '2026-12-12 20:30:00+07',
  'Suất chiếu buổi tối.', 'Rạp Ánh Sao'),
 (6, 'Đêm nhạc Tháng Mười Hai', 'MUSIC', NULL, NULL, 'Nhà hát Hòa Bình', '2026-12-19 19:30:00+07',
  'Mở cửa lúc 18:30.', 'BTC đêm nhạc');
INSERT INTO ticket_types (id, match_id, name, price, total_quantity)
VALUES (1, 1, 'Vé thường', 200000, 20),
       (2, 1, 'VIP', 500000, 5),
       (3, 2, 'Vé thường', 150000, 15),
       (4, 3, 'Vé thường', 120000, 10),
       (5, 4, 'Ghế thường', 85000, 25),
       (6, 5, 'Ghế thường', 110000, 25),
       (7, 6, 'Vé thường', 350000, 60),
       (8, 6, 'VIP', 800000, 10);
ALTER TABLE events ALTER COLUMN id RESTART WITH 100;
ALTER TABLE ticket_types ALTER COLUMN id RESTART WITH 100;
