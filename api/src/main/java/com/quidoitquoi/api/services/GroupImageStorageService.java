package com.quidoitquoi.api.services;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.quidoitquoi.api.exceptions.InvalidGroupImageException;

@Service
public class GroupImageStorageService {

    public static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;

    private final Path storageRoot;

    public GroupImageStorageService(
            @Value("${app.group-image.storage-path:uploads/group-images}") String storagePath) {
        storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public UUID store(UUID groupId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new InvalidGroupImageException("Choose a PNG or JPEG image to upload.");
        }
        if (upload.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidGroupImageException("Group images must be 5 MB or smaller.");
        }

        try {
            byte[] content = upload.getBytes();
            if (content.length > MAX_IMAGE_SIZE_BYTES) {
                throw new InvalidGroupImageException("Group images must be 5 MB or smaller.");
            }
            ValidatedImage image = validateAndNormalize(content);
            UUID imageId = UUID.randomUUID();
            Path groupDirectory = storageRoot.resolve(groupId.toString());
            Files.createDirectories(groupDirectory);

            Path imagePath = imagePath(groupDirectory, imageId, image.extension());
            Path temporaryPath = Files.createTempFile(groupDirectory, "upload-", ".tmp");
            try {
                Files.write(temporaryPath, image.content());
                try {
                    Files.move(temporaryPath, imagePath, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporaryPath, imagePath);
                }
            } catch (IOException exception) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                throw exception;
            }

            return imageId;
        } catch (IOException exception) {
            throw new GroupImageStorageException("Unable to store the group image.", exception);
        }
    }

    public Optional<StoredGroupImage> load(UUID groupId, UUID imageId) {
        Path groupDirectory = storageRoot.resolve(groupId.toString());
        for (String extension : new String[] { "png", "jpg" }) {
            Path imagePath = imagePath(groupDirectory, imageId, extension);
            if (!Files.exists(imagePath)) {
                continue;
            }
            try {
                MediaType mediaType = extension.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
                return Optional.of(new StoredGroupImage(Files.readAllBytes(imagePath), mediaType));
            } catch (IOException exception) {
                throw new GroupImageStorageException("Unable to read the group image.", exception);
            }
        }
        return Optional.empty();
    }

    public void delete(UUID groupId, UUID imageId) throws IOException {
        Path groupDirectory = storageRoot.resolve(groupId.toString());
        Files.deleteIfExists(imagePath(groupDirectory, imageId, "png"));
        Files.deleteIfExists(imagePath(groupDirectory, imageId, "jpg"));
    }

    public String reference(UUID groupId, UUID imageId) {
        return "/api/groups/" + groupId + "/image/" + imageId;
    }

    public Optional<UUID> imageIdFromReference(UUID groupId, String reference) {
        String prefix = "/api/groups/" + groupId + "/image/";
        if (reference == null || !reference.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(reference.substring(prefix.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private ValidatedImage validateAndNormalize(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new InvalidGroupImageException("The uploaded file is not a valid PNG or JPEG image.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidGroupImageException("The uploaded file is not a valid PNG or JPEG image.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                String extension;
                MediaType mediaType;
                if (format.equals("png")) {
                    extension = "png";
                    mediaType = MediaType.IMAGE_PNG;
                } else if (format.equals("jpeg") || format.equals("jpg")) {
                    extension = "jpg";
                    mediaType = MediaType.IMAGE_JPEG;
                } else {
                    throw new InvalidGroupImageException("Only PNG and JPEG images are supported.");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 ||
                        width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION ||
                        (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new InvalidGroupImageException(
                            "Image dimensions must be at most 4096 by 4096 pixels.");
                }

                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidGroupImageException("The uploaded image could not be decoded.");
                }

                try (ByteArrayOutputStream normalized = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(decoded, extension.equals("jpg") ? "jpeg" : "png", normalized)) {
                        throw new GroupImageStorageException("No image encoder is available.");
                    }
                    byte[] normalizedContent = normalized.toByteArray();
                    if (normalizedContent.length > MAX_IMAGE_SIZE_BYTES) {
                        throw new InvalidGroupImageException("Normalized group images must be 5 MB or smaller.");
                    }
                    return new ValidatedImage(extension, mediaType, normalizedContent);
                }
            } finally {
                reader.dispose();
            }
        } catch (InvalidGroupImageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidGroupImageException("The uploaded file is not a valid PNG or JPEG image.", exception);
        }
    }

    private Path imagePath(Path groupDirectory, UUID imageId, String extension) {
        return groupDirectory.resolve(imageId + "." + extension);
    }

    private record ValidatedImage(String extension, MediaType mediaType, byte[] content) {
    }

    public record StoredGroupImage(byte[] content, MediaType mediaType) {
    }

    public static class GroupImageStorageException extends RuntimeException {
        public GroupImageStorageException(String message) {
            super(message);
        }

        public GroupImageStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
