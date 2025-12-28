package com.tiny.platform.core.oauth.security;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * TotpService - 负责 TOTP 验证的实用类。
 *
 * 负责：
 *  - 清洗/解码 Base32 secret（忽略空格与大小写）
 *  - 复用 TimeBasedOneTimePasswordGenerator（避免每次 new）
 *  - 支持 +/- N steps 的时间窗口（默认 +/-1）
 */
@Service
public class TotpService {

    private static final Logger logger = LoggerFactory.getLogger(TotpService.class);

    private static final TimeBasedOneTimePasswordGenerator GENERATOR = createGenerator();

    private static final Base32 BASE32 = new Base32();

    private static TimeBasedOneTimePasswordGenerator createGenerator() {
        try {
            // 默认 6 位、30 秒步长、HmacSHA1（与 Google Authenticator 兼容）
            return new TimeBasedOneTimePasswordGenerator();
        } catch (Exception e) {
            // 极少会发生，记录并重新抛出运行时异常以便早期发现问题
            logger.error("无法初始化 TOTP 生成器", e);
            throw new IllegalStateException("TOTP 生成器初始化失败", e);
        }
    }

    /**
     * 验证给定的 code 是否与 secret 匹配。
     *
     * @param base32Secret  Base32 编码的 secret（可能包含空格、大小写混合）
     * @param submittedCode 用户提供的验证码（6 位字符串）
     * @param allowedWindow 包含当前步长和前后 allowedWindow 个步长（例如 allowedWindow = 1 表示 ±1）
     * @return 是否验证通过
     */
    public boolean verify(String base32Secret, String submittedCode, int allowedWindow) {
        if (base32Secret == null || base32Secret.isBlank()) {
            logger.debug("空的 TOTP secret");
            return false;
        }

        if (submittedCode == null || submittedCode.isBlank()) {
            logger.debug("空的 submittedCode");
            return false;
        }

        try {
            byte[] keyBytes = decodeBase32(base32Secret);
            if (keyBytes == null || keyBytes.length == 0) {
                logger.warn("TOTP secret 解码为空");
                return false;
            }

            Key key = new SecretKeySpec(keyBytes, GENERATOR.getAlgorithm());
            long stepMillis = GENERATOR.getTimeStep().toMillis();
            long now = System.currentTimeMillis();

            // 允许 ± allowedWindow 步长（例如 1 => -1, 0, +1）
            for (int offset = -allowedWindow; offset <= allowedWindow; offset++) {
                Instant instant = Instant.ofEpochMilli(now + offset * stepMillis);
                int otp = GENERATOR.generateOneTimePassword(key, instant);
                String candidate = String.format(Locale.ROOT, "%0" + GENERATOR.getPasswordLength() + "d", otp);

                if (Objects.equals(candidate, submittedCode)) {
                    return true;
                }
            }

            return false;
        } catch (IllegalArgumentException e) {
            logger.warn("TOTP secret 解码失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("TOTP 验证异常", e);
            return false;
        }
    }

    /**
     * 默认允许 ±1 时间步长
     */
    public boolean verify(String base32Secret, String submittedCode) {
        return verify(base32Secret, submittedCode, 1);
    }

    /**
     * 清洗并解码 Base32 secret。
     * - 去掉所有空白字符
     * - 转为大写（Base32 对大小写不敏感）
     *
     * @throws IllegalArgumentException 当 secret 无法被 Base32 解码时抛出
     */
    private byte[] decodeBase32(String secret) {
        String cleaned = secret.replaceAll("\s+", "").toUpperCase(Locale.ROOT);
        // commons-codec 的 Base32 解码在非法输入时会抛出 IllegalArgumentException
        return BASE32.decode(cleaned);
    }
}
