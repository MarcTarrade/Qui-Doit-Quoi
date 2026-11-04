package com.quidoitquoi.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.quidoitquoi.api.exceptions.InvalidGroupImageException;

class GroupImageStorageServiceTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir
    Path storagePath;

    private GroupImageStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new GroupImageStorageService(storagePath.toString());
    }

    @Test
    void storesNormalizedPngAndLoadsItByGeneratedId() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "image", "trip.png", MediaType.IMAGE_PNG_VALUE, pngImage());

        UUID imageId = storage.store(GROUP_ID, upload);

        var storedImage = storage.load(GROUP_ID, imageId).orElseThrow();
        assertThat(ImageIO.read(new ByteArrayInputStream(storedImage.content()))).isNotNull();
        assertThat(storedImage.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        try (var storedFiles = Files.list(storagePath.resolve(GROUP_ID.toString()))) {
            assertThat(storedFiles).hasSize(1);
        }
    }

    @Test
    void storesProfileImagesInTheUserImageDirectory() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "image", "avatar.png", MediaType.IMAGE_PNG_VALUE, pngImage());

        UUID imageId = storage.storeUserImage(GROUP_ID, upload);

        assertThat(storage.userImageReference(GROUP_ID, imageId))
                .isEqualTo("/api/users/" + GROUP_ID + "/image/" + imageId);
        assertThat(storage.userImageIdFromReference(
                GROUP_ID, storage.userImageReference(GROUP_ID, imageId)))
                .contains(imageId);
        assertThat(storage.loadUserImage(GROUP_ID, imageId)).isPresent();
    }

    @Test
    void rejectsNonImagePayloadEvenWhenFilenameLooksLikeAnImage() {
        MockMultipartFile upload = new MockMultipartFile(
                "image", "not-an-image.png", MediaType.IMAGE_PNG_VALUE, "not an image".getBytes());

        assertThatThrownBy(() -> storage.store(GROUP_ID, upload))
                .isInstanceOf(InvalidGroupImageException.class);
    }

    @Test
    void rejectsImagesOverConfiguredSize() {
        byte[] oversized = new byte[(int) GroupImageStorageService.MAX_IMAGE_SIZE_BYTES + 1];
        MockMultipartFile upload = new MockMultipartFile(
                "image", "large.png", MediaType.IMAGE_PNG_VALUE, oversized);

        assertThatThrownBy(() -> storage.store(GROUP_ID, upload))
                .isInstanceOf(InvalidGroupImageException.class)
                .hasMessage("api.error.image-too-large");
    }

    private byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        }
    }
}
