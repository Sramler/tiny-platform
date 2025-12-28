package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.model.UserAvatar;
import com.tiny.platform.core.oauth.repository.UserAvatarRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.user.service.AvatarService;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * 用户头像服务实现
 * 提供头像上传、获取、删除等功能
 */
@Service
public class AvatarServiceImpl implements AvatarService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarServiceImpl.class);

    // 文件大小限制：1MB
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB

    // 允许的MIME类型
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp"
    );

    // 头像尺寸（正方形）
    private static final int AVATAR_SIZE = 200;

    private final UserAvatarRepository avatarRepository;
    private final UserRepository userRepository;

    @Autowired
    public AvatarServiceImpl(UserAvatarRepository avatarRepository, UserRepository userRepository) {
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public boolean uploadAvatar(Long userId, InputStream inputStream, String contentType, String filename, long fileSize) {
        try {
            // 1. 验证文件大小
            if (fileSize > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件大小不能超过 " + (MAX_FILE_SIZE / 1024) + "KB");
            }

            // 2. 验证文件类型
            if (!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                throw new IllegalArgumentException("不支持的文件类型，仅支持 PNG、JPEG、WebP 格式");
            }

            // 3. 读取文件数据
            byte[] fileData = inputStream.readAllBytes();

            // 4. 验证并处理图片（压缩/缩放）
            byte[] processedData = processImage(fileData, contentType);

            // 5. 计算文件哈希（使用处理后的数据）
            String contentHash = calculateSHA256(processedData);

            // 6. 验证用户是否存在（不需要加载整个 User 实体）
            if (!userRepository.existsById(userId)) {
                throw new IllegalArgumentException("用户不存在: " + userId);
            }

            // 7. 先删除已存在的头像（如果存在）
            avatarRepository.findByUserId(userId).ifPresent(existing -> {
                avatarRepository.delete(existing);
                avatarRepository.flush(); // 立即执行删除，确保后续插入不会冲突
            });

            // 8. 创建新的头像实体（直接设置 userId，不依赖 @MapsId）
            UserAvatar avatar = new UserAvatar();
            avatar.setUserId(userId); // 直接设置 userId，避免乐观锁问题
            avatar.setContentType(contentType);
            avatar.setFilename(filename);
            avatar.setFileSize(processedData.length);
            avatar.setData(processedData);
            avatar.setContentHash(contentHash);
            avatarRepository.saveAndFlush(avatar);

            logger.info("用户 {} 头像上传成功，大小: {} 字节", userId, processedData.length);
            return true;

        } catch (IllegalArgumentException e) {
            logger.warn("用户 {} 头像上传失败: {}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("用户 {} 头像上传异常", userId, e);
            throw new RuntimeException("头像上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getAvatarData(Long userId) {
        return avatarRepository.findByUserId(userId)
                .map(UserAvatar::getData)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AvatarMetadata getAvatarMetadata(Long userId) {
        return avatarRepository.findByUserId(userId)
                .map(avatar -> new AvatarMetadata(
                        avatar.getContentType(),
                        avatar.getFilename(),
                        avatar.getFileSize(),
                        avatar.getContentHash()
                ))
                .orElse(null);
    }

    @Override
    @Transactional
    public boolean deleteAvatar(Long userId) {
        try {
            if (avatarRepository.existsByUserId(userId)) {
                avatarRepository.deleteByUserId(userId);
                logger.info("用户 {} 头像删除成功", userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("用户 {} 头像删除异常", userId, e);
            throw new RuntimeException("头像删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理图片：压缩/缩放为指定尺寸
     */
    private byte[] processImage(byte[] imageData, String contentType) throws IOException {
        try {
            // 读取原始图片
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (originalImage == null) {
                throw new IOException("无法读取图片数据，请确保文件是有效的图片格式");
            }

            // 计算缩放尺寸（保持宽高比，取较小边）
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            int targetWidth, targetHeight;

            if (originalWidth > originalHeight) {
                targetHeight = AVATAR_SIZE;
                targetWidth = (int) (originalWidth * ((double) AVATAR_SIZE / originalHeight));
            } else {
                targetWidth = AVATAR_SIZE;
                targetHeight = (int) (originalHeight * ((double) AVATAR_SIZE / originalWidth));
            }

            // 如果是正方形，直接缩放；否则裁剪为正方形
            BufferedImage resizedImage;
            if (targetWidth == targetHeight) {
                // 直接缩放
                resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resizedImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
                g.dispose();
            } else {
                // 缩放后居中裁剪为正方形
                BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaledImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
                g.dispose();

                // 裁剪为正方形（居中）
                int cropX = (targetWidth - AVATAR_SIZE) / 2;
                int cropY = (targetHeight - AVATAR_SIZE) / 2;
                resizedImage = scaledImage.getSubimage(cropX, cropY, AVATAR_SIZE, AVATAR_SIZE);
            }

            // 转换为字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String format = contentType.contains("png") ? "png" : contentType.contains("webp") ? "webp" : "jpg";
            ImageIO.write(resizedImage, format, baos);

            return baos.toByteArray();

        } catch (IOException e) {
            logger.error("图片处理失败", e);
            throw new IOException("图片处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算SHA-256哈希值
     */
    private String calculateSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256算法不可用", e);
            throw new RuntimeException("哈希计算失败", e);
        }
    }
}
