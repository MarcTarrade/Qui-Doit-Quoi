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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Path userStorageRoot;

    @Autowired
    public GroupImageStorageService(
            @Value("${app.group-image.storage-path:uploads/group-images}") String storagePath,
            @Value("${app.user-image.storage-path:uploads/user-images}") String userStoragePath) {
        storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        userStorageRoot = Path.of(userStoragePath).toAbsolutePath().normalize();
    }

    public GroupImageStorageService(String storagePath) {
        this(storagePath, storagePath);
    }

    public UUID store(UUID groupId, MultipartFile upload) {
        return store(storageRoot, groupId, upload);
    }

    public UUID storeUserImage(UUID userId, MultipartFile upload) {
        return store(userStorageRoot, userId, upload);
    }

    private UUID store(Path root, UUID ownerId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new InvalidGroupImageException("api.error.image-empty");
        }
        if (upload.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidGroupImageException("api.error.image-too-large");
        }

        try {
            byte[] content = upload.getBytes();
            if (content.length > MAX_IMAGE_SIZE_BYTES) {
                throw new InvalidGroupImageException("api.error.image-too-large");
            }
            ValidatedImage image = validateAndNormalize(content);
            UUID imageId = UUID.randomUUID();
            Path ownerDirectory = root.resolve(ownerId.toString());
            Files.createDirectories(ownerDirectory);

            Path imagePath = imagePath(ownerDirectory, imageId, image.extension());
            Path temporaryPath = Files.createTempFile(ownerDirectory, "upload-", ".tmp");
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
        return load(storageRoot, groupId, imageId);
    }

    public Optional<StoredGroupImage> loadUserImage(UUID userId, UUID imageId) {
        return load(userStorageRoot, userId, imageId);
    }

    private Optional<StoredGroupImage> load(Path root, UUID ownerId, UUID imageId) {
        Path ownerDirectory = root.resolve(ownerId.toString());
        for (String extension : new String[] { "png", "jpg" }) {
            Path imagePath = imagePath(ownerDirectory, imageId, extension);
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
        delete(storageRoot, groupId, imageId);
    }

    public void deleteUserImage(UUID userId, UUID imageId) throws IOException {
        delete(userStorageRoot, userId, imageId);
    }

    private void delete(Path root, UUID ownerId, UUID imageId) throws IOException {
        Path ownerDirectory = root.resolve(ownerId.toString());
        Files.deleteIfExists(imagePath(ownerDirectory, imageId, "png"));
        Files.deleteIfExists(imagePath(ownerDirectory, imageId, "jpg"));
    }

    public String reference(UUID groupId, UUID imageId) {
        return "/api/groups/" + groupId + "/image/" + imageId;
    }

    public Optional<UUID> imageIdFromReference(UUID groupId, String reference) {
        return imageIdFromReference("/api/groups/" + groupId + "/image/", reference);
    }

    public String userImageReference(UUID userId, UUID imageId) {
        return "/api/users/" + userId + "/image/" + imageId;
    }

    public Optional<UUID> userImageIdFromReference(UUID userId, String reference) {
        return imageIdFromReference("/api/users/" + userId + "/image/", reference);
    }

    private Optional<UUID> imageIdFromReference(String prefix, String reference) {
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
                throw new InvalidGroupImageException("api.error.image-invalid");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidGroupImageException("api.error.image-invalid");
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
                    throw new InvalidGroupImageException("api.error.image-unsupported");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 ||
                        width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION ||
                        (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new InvalidGroupImageException("api.error.image-dimensions");
                }

                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidGroupImageException("api.error.image-decode");
                }

                try (ByteArrayOutputStream normalized = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(decoded, extension.equals("jpg") ? "jpeg" : "png", normalized)) {
                        throw new GroupImageStorageException("No image encoder is available.");
                    }
                    byte[] normalizedContent = normalized.toByteArray();
                    if (normalizedContent.length > MAX_IMAGE_SIZE_BYTES) {
                        throw new InvalidGroupImageException("api.error.image-normalized-too-large");
                    }
                    return new ValidatedImage(extension, mediaType, normalizedContent);
                }
            } finally {
                reader.dispose();
            }
        } catch (InvalidGroupImageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidGroupImageException("api.error.image-invalid", exception);
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
