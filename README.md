# Event Booking

Ứng dụng web đặt vé sự kiện, hỗ trợ bóng đá, phim và ca nhạc. Đây là dự án thực tập viết bằng Java 17, Spring Boot, Spring Security, PostgreSQL và HTML/CSS/JavaScript. Maven Wrapper đi kèm mã nguồn.

## Chức năng

- Khách truy cập xem, lọc và tìm kiếm sự kiện.
- Người dùng đăng ký, đăng nhập, đặt vé, theo dõi và hủy đơn, thanh toán giả lập và tải phiếu xác nhận PDF.
- Quản trị viên quản lý danh mục, sự kiện, ảnh bìa, loại vé và trạng thái mở bán.
- Trợ lý Groq hỗ trợ tìm vé bằng tiếng Việt theo danh mục, thời gian, địa điểm hoặc giá. Khi hỏi về một sự kiện, trợ lý trả lời dựa trên dữ liệu của sự kiện đó.

## Chạy demo trên Windows

Cài JDK 17. Mở PowerShell trong thư mục dự án và chạy:

```powershell
.\mvnw.cmd -Pdemo spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Mở [trang sự kiện](http://localhost:8085/events). Bản demo dùng H2 trong bộ nhớ và có dữ liệu mẫu. Dữ liệu thay đổi trong phiên demo sẽ mất khi dừng ứng dụng.

Để chạy cả trợ lý Groq, dùng script bên dưới thay cho lệnh Maven trực tiếp. Script sẽ đọc khóa đã lưu trên máy hoặc yêu cầu nhập khóa khi chạy.

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\start-demo.ps1 -Port 8085
```

Dùng [cùng một trang đăng nhập](http://localhost:8085/login) cho cả hai vai trò:

| Vai trò demo | Email | Mật khẩu |
| --- | --- | --- |
| Quản trị viên | `admin@demo.example` | `AdminDemo2026!` |
| Khách hàng | `khach@demo.example` | `KhachDemo2026!` |

### Các trang chính

| Trang | Đường dẫn |
| --- | --- |
| Danh sách sự kiện | `/events` |
| Đăng nhập và đăng ký | `/login`, `/register` |
| Đơn của tôi | `/my-bookings` |
| Quản trị | `/admin` |
| Tài liệu và thử API | `/swagger-ui.html` |

## Chạy với PostgreSQL

1. Tạo tài khoản PostgreSQL `football_app`, đặt mật khẩu và tạo cơ sở dữ liệu `football_booking` với tài khoản này làm chủ sở hữu. Cấu hình mặc định dùng `localhost:5432`.
2. Trong PowerShell, đặt biến môi trường `DB_PASSWORD` cho phiên đang chạy. Nếu dùng cấu hình kết nối khác, đặt thêm `SPRING_DATASOURCE_URL` và `SPRING_DATASOURCE_USERNAME`.
3. Nếu cần khởi tạo tài khoản quản trị, đặt cả `BOOKING_ADMIN_EMAIL` và `BOOKING_ADMIN_PASSWORD`. Để bật trợ lý, đặt thêm `GROQ_API_KEY`. Lưu các giá trị này ngoài mã nguồn.
4. Chạy ứng dụng. Cấu trúc cơ sở dữ liệu được khởi tạo từ `src/main/resources/schema.sql`. Mở [trang sự kiện ở cổng 8080](http://localhost:8080/events).

```powershell
.\mvnw.cmd spring-boot:run
```

## Kiểm thử

```powershell
.\mvnw.cmd test
```

Bộ kiểm thử mặc định dùng H2. Để chạy thêm nhóm kiểm thử PostgreSQL, đặt `TEST_PG_URL`, `TEST_PG_USER` và `TEST_PG_PASSWORD` trỏ tới một cơ sở dữ liệu kiểm thử riêng.

Trang khách hàng mặc định dùng kịch bản thanh toán thành công. Để kiểm tra các trường hợp lỗi qua API trên bản demo, bật `booking.demo.payment-scenarios-enabled` cho phiên chạy. Ví dụ trên PowerShell:

```powershell
$env:SPRING_APPLICATION_JSON = '{"booking":{"demo":{"payment-scenarios-enabled":true}}}'
.\mvnw.cmd -Pdemo spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Sau đó đăng nhập bằng tài khoản khách và gửi `POST /api/bookings/{id}/pay` với `scenario` là `FAIL_BEFORE_CHARGE`, `TIMEOUT` hoặc `FAIL_AFTER_CHARGE`. Khi kiểm tra từ một HTTP client, dùng phiên đăng nhập của chủ đơn và gửi CSRF token ở header theo phản hồi của `GET /api/auth/csrf`.
