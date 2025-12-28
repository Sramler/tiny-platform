package com.tiny.platform.core.oauth.service;

import java.io.InputStream;

/**
 * 用户头像服务接口
 */
public interface AvatarService {

    /**
     * 上传用户头像
     * @param userId 用户ID
     * @param inputStream 文件输入流
     * @param contentType MIME类型
     * @param filename 原始文件名
     * @param fileSize 文件大小
     * @return 是否成功
     * @throws IllegalArgumentException 如果文件类型、大小不合法
     */
    boolean uploadAvatar(Long userId, InputStream inputStream, String contentType, String filename, long fileSize);

    /**
     * 获取用户头像数据
     * @param userId 用户ID
     * @return 头像数据（字节数组），如果不存在则返回null
     */
    byte[] getAvatarData(Long userId);

    /**
     * 获取用户头像元信息
     * @param userId 用户ID
     * @return 头像元信息，如果不存在则返回null
     */
    AvatarMetadata getAvatarMetadata(Long userId);

    /**
     * 删除用户头像
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteAvatar(Long userId);

    /**
     * 头像元信息
     */
    class AvatarMetadata {
        private String contentType;
        private String filename;
        private Integer fileSize;
        private String contentHash;

        public AvatarMetadata(String contentType, String filename, Integer fileSize, String contentHash) {
            this.contentType = contentType;
            this.filename = filename;
            this.fileSize = fileSize;
            this.contentHash = contentHash;
        }

        public String getContentType() {
            return contentType;
        }

        public String getFilename() {
            return filename;
        }

        public Integer getFileSize() {
            return fileSize;
        }

        public String getContentHash() {
            return contentHash;
        }
    }
}
