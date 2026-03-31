package com.tiny.platform.core.oauth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.core.oauth.model.UserAvatar;
import com.tiny.platform.core.oauth.repository.UserAvatarRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.service.TenantQuotaService;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AvatarServiceImplTest {

    private UserAvatarRepository avatarRepository;
    private UserRepository userRepository;
    private TenantQuotaService tenantQuotaService;
    private AvatarServiceImpl avatarService;

    @BeforeEach
    void setUp() {
        avatarRepository = org.mockito.Mockito.mock(UserAvatarRepository.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        tenantQuotaService = org.mockito.Mockito.mock(TenantQuotaService.class);
        avatarService = new AvatarServiceImpl(avatarRepository, userRepository, tenantQuotaService);
        TenantContext.setActiveTenantId(88L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadAvatar_shouldCheckTenantStorageQuotaBeforePersisting() throws IOException {
        byte[] imageBytes = pngBytes(4, 4);
        when(userRepository.existsById(7L)).thenReturn(true);
        when(avatarRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(avatarRepository.saveAndFlush(any(UserAvatar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean uploaded = avatarService.uploadAvatar(
            7L,
            new ByteArrayInputStream(imageBytes),
            "image/png",
            "avatar.png",
            imageBytes.length
        );

        assertThat(uploaded).isTrue();
        ArgumentCaptor<Long> additionalBytesCaptor = ArgumentCaptor.forClass(Long.class);
        verify(tenantQuotaService).assertStorageQuotaAvailable(
            eq(88L),
            additionalBytesCaptor.capture(),
            eq("上传头像")
        );
        assertThat(additionalBytesCaptor.getValue()).isPositive();
        verify(avatarRepository).saveAndFlush(any(UserAvatar.class));
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
