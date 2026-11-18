# Football Booking Backend

Backend đặt vé bóng đá dùng Java 17, Spring Boot và PostgreSQL. Mốc hiện tại có quản lý trận đấu và loại vé, tìm kiếm, đăng ký khách hàng, đặt vé, thanh toán giả lập và hủy đơn. PDF xác nhận và trợ lý AI thuộc các mốc tiếp theo.

## Chạy ở máy cá nhân

Mở terminal PowerShell ở thư mục có `pom.xml`. Cấu hình hiện dùng PostgreSQL tại `localhost:5432`, database `football_booking` và tài khoản `football_app`. Trong pgAdmin, đặt `football_app` làm chủ database hoặc cấp quyền tạo bảng trong schema `public`.

```powershell
$credential = Get-Credential -UserName football_app -Message "Nhap mat khau PostgreSQL"
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
.\mvnw.cmd spring-boot:run
```

Spring Boot chạy `schema.sql` để tạo các bảng còn thiếu và bổ sung cột tồn kho cho bảng loại vé hiện có. Database mới bắt đầu với danh sách trống. Hibernate kiểm tra các bảng do JPA quản lý khi khởi động.

## API hiện có

| Phương thức | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | `/api/events` hoặc `/football` | Danh sách trận đấu và loại vé |
| GET | `/api/events/{id}` | Chi tiết một trận đấu |
| POST | `/api/events` | Tạo trận đấu, có thể gửi kèm loại vé |
| PUT | `/api/events/{id}` | Sửa thông tin trận đấu, giữ các loại vé hiện có |
| POST | `/api/events/{id}/ticket-types` | Thêm một loại vé cho trận đấu |
| POST | `/api/customers` | Đăng ký khách hàng bằng tên và email |
| GET | `/api/customers/{id}/bookings` | Xem lịch sử đơn theo mã khách hàng |
| POST | `/api/bookings` | Tạo đơn PENDING và giữ vé |
| GET | `/api/bookings/{id}` | Xem chi tiết đơn và đơn giá tại lúc đặt |
| POST | `/api/bookings/{id}/pay` | Thanh toán giả lập và trả về đơn CONFIRMED |
| POST | `/api/bookings/{id}/cancel` | Hủy đơn và trả vé |

Thêm `keyword`, `venue`, `from`, `to`, `page` hoặc `size` vào `GET /api/events` để nhận kết quả phân trang. Ví dụ `/api/events?keyword=ha%20noi&page=0&size=10`. `from` bao gồm thời điểm bắt đầu và `to` loại trừ thời điểm kết thúc. Dùng giờ ISO 8601, chẳng hạn `2026-12-01T00:00:00Z`. Kết quả có `content`, `page`, `size`, `totalElements` và `totalPages`. Khi gọi không kèm các tham số trên, endpoint trả mảng như bản trước. Tất cả trận đấu trong phạm vi dự án thuộc thể loại SPORTS.

Để demo, giữ ứng dụng chạy và mở thêm một terminal PowerShell:

```powershell
$body = @{
    homeTeam = 'Ha Noi'
    awayTeam = 'Hai Phong'
    stadium = 'Hang Day'
    startTime = '2026-12-08T12:00:00Z'
    organizer = 'BTC'
    ticketTypes = @(@{ name = 'VIP'; price = 500000; totalQuantity = 20 })
} | ConvertTo-Json -Depth 4

$match = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/events' `
    -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri "http://localhost:8080/api/events/$($match.id)" | ConvertTo-Json -Depth 4
```

Sau khi tạo trận mẫu bằng lệnh trên, tiếp tục trong cùng terminal:

```powershell
$api = 'http://localhost:8080/api'
$customerBody = @{
    name = 'Khach demo'
    email = "demo-$([guid]::NewGuid().ToString('N'))@example.com"
} | ConvertTo-Json
$customer = Invoke-RestMethod -Method Post -Uri "$api/customers" -ContentType 'application/json' -Body $customerBody

$bookingBody = @{
    customerId = $customer.id
    eventId = $match.id
    items = @(@{ ticketTypeId = $match.ticketTypes[0].id; quantity = 2 })
} | ConvertTo-Json -Depth 4
$booking = Invoke-RestMethod -Method Post -Uri "$api/bookings" -ContentType 'application/json' -Body $bookingBody
$booking | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri "$api/bookings/$($booking.id)/pay"
Invoke-RestMethod -Method Post -Uri "$api/bookings/$($booking.id)/cancel"
Invoke-RestMethod -Uri "$api/customers/$($customer.id)/bookings" | ConvertTo-Json -Depth 4
```

Với loại vé mẫu có 20 vé, `availableQuantity` giảm xuống 18 khi tạo đơn và trở về 20 khi hủy. Yêu cầu vượt số vé còn lại trả HTTP 409. Thanh toán được mô phỏng trong một giao dịch, lưu thời điểm thanh toán và chuyển PENDING → PAID → CONFIRMED. Gọi lại thanh toán trên đơn CONFIRMED giữ trạng thái hiện có. Gọi lại hủy trên đơn CANCELLED giữ nguyên tồn kho. Đơn PENDING giữ vé cho đến khi hủy hoặc thanh toán.

API hiện dùng mã khách hàng để demo. Đăng nhập và kiểm tra chủ đơn là phần mở rộng cần có trước khi triển khai công khai.

## Kiểm thử và đọc code

Chạy `.\mvnw.cmd test`. Kiểm thử dùng H2 trong bộ nhớ, gồm hai yêu cầu cùng đặt vé cuối, hoàn tác một đơn thiếu vé và hủy lặp.

Bắt đầu ở Controller để xem URL và dữ liệu đầu vào. Controller gọi interface `FootballMatchService` hoặc `BookingService`. Spring cung cấp lớp triển khai tương ứng. Mở lớp có hậu tố `Impl` để xem kiểm tra dữ liệu, quy tắc nghiệp vụ và ranh giới transaction.

`FootballMatchRepository` và `TicketTypeRepository` truy cập dữ liệu qua Spring Data JPA. `BookingRepository` giữ các câu SQL dùng JDBC. Khi đặt vé, `BookingServiceImpl` gọi các thao tác giữ vé và tạo đơn trong cùng một transaction. Nếu câu UPDATE có điều kiện không cập nhật được dòng nào, Service từ chối đơn và PostgreSQL hoàn tác các lượt giữ vé của đơn đó.
