package com.example.footballbooking.model;

import com.example.footballbooking.exception.InvalidRequestException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

public record EventCover(String contentType, byte[] data) {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_EDGE = 6000;
    private static final long MAX_PIXELS = 16_000_000;

    public static EventCover decode(byte[] upload) {
        if (upload.length == 0) {
            throw new InvalidRequestException("Hãy chọn một ảnh JPG hoặc PNG.");
        }
        if (upload.length > MAX_BYTES) {
            throw new InvalidRequestException("Ảnh bìa tối đa 4 MB.");
        }

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String contentType = switch (format) {
                    case "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> throw invalidImage();
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE
                        || (long) width * height > MAX_PIXELS) {
                    throw new InvalidRequestException("Ảnh tối đa 6.000 pixel mỗi chiều và 16 megapixel.");
                }
                var pixels = reader.read(0);
                var encoded = new ByteArrayOutputStream();
                if (!ImageIO.write(pixels, format, encoded)) {
                    throw invalidImage();
                }
                if (encoded.size() > MAX_BYTES) {
                    throw new InvalidRequestException("Ảnh sau khi xử lý vượt 4 MB. Hãy chọn ảnh nhỏ hơn.");
                }
                return new EventCover(contentType, encoded.toByteArray());
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw invalidImage();
        }
    }

    private static InvalidRequestException invalidImage() {
        return new InvalidRequestException("Ảnh không hợp lệ. Hãy chọn ảnh JPG hoặc PNG.");
    }
}
