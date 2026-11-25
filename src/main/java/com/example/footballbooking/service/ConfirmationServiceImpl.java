package com.example.footballbooking.service;

import com.example.footballbooking.exception.BookingException;
import com.example.footballbooking.repository.BookingRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmationServiceImpl implements ConfirmationService {
    private final BookingService bookings;
    private final EventService matches;
    private final BookingRepository repository;

    public ConfirmationServiceImpl(BookingService bookings, EventService matches, BookingRepository repository) {
        this.bookings = bookings;
        this.matches = matches;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] createPdf(long bookingId) {
        var booking = bookings.get(bookingId);
        if (!booking.status().equals("CONFIRMED")) {
            throw new BookingException(HttpStatus.CONFLICT, "Only confirmed bookings have a PDF confirmation");
        }
        var match = matches.getEvent(booking.eventId());
        var customer = repository.findCustomer(booking.customerId()).orElseThrow();
        var dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        List<String> lines = new ArrayList<>();
        lines.add("XÁC NHẬN ĐẶT VÉ");
        lines.add("Mã đơn: " + booking.reference());
        lines.add("Trạng thái: " + booking.status());
        lines.add("Khách hàng: " + customer.name());
        lines.add("Email: " + customer.email());
        lines.add("Sự kiện: " + match.getTitle());
        lines.add("Địa điểm: " + match.getVenue());
        lines.add("Giờ bắt đầu: " + dateFormat.format(match.getStartTime()) + " (giờ Việt Nam)");
        lines.add("");
        for (var item : booking.items()) {
            String name = match.getTicketTypes().stream().filter(t -> t.getId() == item.ticketTypeId())
                .findFirst().map(t -> t.getName()).orElse("Vé #" + item.ticketTypeId());
            lines.add(name + " | Số lượng: " + item.quantity() + " | Đơn giá: "
                + item.unitPrice().toPlainString() + " VND");
        }
        lines.add("");
        lines.add("Tổng tiền: " + booking.totalAmount().toPlainString() + " VND");
        lines.add("Thanh toán giả lập lúc: " + dateFormat.format(booking.paidAt()));
        return render(lines);
    }

    private byte[] render(List<String> lines) {
        try (var document = new PDDocument();
             var fontStream = new ClassPathResource("fonts/NotoSans-Regular.ttf").getInputStream();
             var output = new ByteArrayOutputStream()) {
            var font = PDType0Font.load(document, fontStream);
            List<String> wrapped = new ArrayList<>();
            for (String line : lines) {
                StringBuilder current = new StringBuilder();
                // Wrap by code point so Vietnamese and long names remain readable.
                for (int point : line.replaceAll("[\\p{Cntrl}]", " ").codePoints().toArray()) {
                    String character = new String(Character.toChars(point));
                    try {
                        font.getStringWidth(character);
                    } catch (IllegalArgumentException ex) {
                        character = "?";
                    }
                    if (font.getStringWidth(current + character) * 11 / 1000 > 500) {
                        wrapped.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(character);
                }
                wrapped.add(current.toString());
            }
            for (int offset = 0; offset < wrapped.size(); offset += 36) {
                var page = new PDPage();
                document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 11);
                    content.setLeading(20);
                    content.newLineAtOffset(48, 735);
                    for (String line : wrapped.subList(offset, Math.min(offset + 36, wrapped.size()))) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BookingException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate the PDF confirmation");
        }
    }
}
