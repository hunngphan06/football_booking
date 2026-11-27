# Event Booking

Ứng dụng web đặt vé bóng đá, phim và ca nhạc. Dự án dùng Java 17, Spring Boot, Spring Security, PostgreSQL và HTML/CSS/JavaScript. Maven Wrapper đi kèm mã nguồn.

## Chức năng

- Khách xem, lọc và tìm kiếm sự kiện.
- Người dùng đăng ký, đặt vé, thanh toán giả lập, hủy đơn và tải phiếu xác nhận PDF.
- Quản trị viên quản lý danh mục, sự kiện, ảnh bìa, loại vé và trạng thái mở bán.
- Trợ lý Groq hỗ trợ tìm vé và hỏi đáp bằng tiếng Việt dựa trên dữ liệu sự kiện.

## Chạy với PostgreSQL trên Windows

### 1. Chuẩn bị cơ sở dữ liệu

Cài JDK 17 và khởi động PostgreSQL. Nếu bạn đã có cơ sở dữ liệu và tài khoản như dưới đây, dùng lại cấu hình hiện có.

| Cấu hình | Giá trị mặc định |
| --- | --- |
| Máy chủ | `localhost:5432` |
| Cơ sở dữ liệu | `football_booking` |
| Tài khoản PostgreSQL | `football_app` |

Trong pgAdmin, tạo role `football_app`, bật quyền đăng nhập và đặt mật khẩu. Tạo database `football_booking` với owner là `football_app`.

### 2. Đặt mật khẩu kết nối

Mở PowerShell trong thư mục dự án. Nhập **mật khẩu của role `football_app`** khi được hỏi.

```powershell
$dbCredential = Get-Credential -UserName football_app -Message "Nhap mat khau PostgreSQL"
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
```

Nếu dùng máy chủ, cổng hoặc tên database khác, đặt `SPRING_DATASOURCE_URL`. Nếu dùng role khác, đặt `SPRING_DATASOURCE_USERNAME` và nhập mật khẩu của role đó.

### 3. Khởi tạo tài khoản quản trị và bật trợ lý nếu cần

Để tạo tài khoản quản trị mới, chạy các lệnh sau trong cùng cửa sổ PowerShell trước khi khởi động. Nhập email và mật khẩu bạn muốn dùng trên trang đăng nhập.

```powershell
$env:BOOKING_ADMIN_EMAIL = Read-Host "Nhap email quan tri"
$adminSecret = Read-Host "Nhap mat khau quan tri" -AsSecureString
$env:BOOKING_ADMIN_PASSWORD = [pscredential]::new('admin', $adminSecret).GetNetworkCredential().Password
```

Để dùng trợ lý AI, đặt `GROQ_API_KEY` trong cùng cửa sổ PowerShell:

```powershell
$groqSecret = Read-Host "Nhap Groq API key" -AsSecureString
$env:GROQ_API_KEY = [pscredential]::new('groq', $groqSecret).GetNetworkCredential().Password
```

### 4. Khởi động ứng dụng

```powershell
.\mvnw.cmd spring-boot:run
```

Spring Boot khởi tạo cấu trúc cơ sở dữ liệu từ `src/main/resources/schema.sql`. Đăng ký tài khoản khách ở [trang đăng ký](http://localhost:8080/register). Khi khởi động lại ứng dụng với cùng database, bạn có thể đăng nhập bằng tài khoản đã tạo và xem lại các đơn đã đặt.

| Trang | Đường dẫn |
| --- | --- |
| Danh sách sự kiện | [`/events`](http://localhost:8080/events) |
| Đăng nhập | [`/login`](http://localhost:8080/login) |
| Đơn của tôi | [`/my-bookings`](http://localhost:8080/my-bookings) |
| Quản trị | [`/admin`](http://localhost:8080/admin) |
| Swagger UI | [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) |

## Kiểm thử với PostgreSQL

Tạo một database kiểm thử riêng. Đặt `TEST_PG_URL`, `TEST_PG_USER` và `TEST_PG_PASSWORD` trỏ tới database kiểm thử, rồi chạy:

```powershell
.\mvnw.cmd "-Dtest=PostgresBookingTests" test
```
