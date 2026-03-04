package com.tiny.platform.core.oauth.security;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService service = new TotpService();

    @Test
    void should_verify_valid_totp_and_handle_window_and_formatting() throws Exception {
        byte[] secretBytes = "12345678901234567890".getBytes(StandardCharsets.UTF_8);
        String base32Secret = new Base32().encodeToString(secretBytes).toLowerCase(Locale.ROOT);

        TimeBasedOneTimePasswordGenerator generator = new TimeBasedOneTimePasswordGenerator();
        Key key = new SecretKeySpec(secretBytes, generator.getAlgorithm());
        int otp = generator.generateOneTimePassword(key, Instant.now());
        String code = String.format(Locale.ROOT, "%06d", otp);

        assertThat(service.verify(" " + base32Secret + " ", code, 0)).isTrue();
        assertThat(service.verify(base32Secret, code)).isTrue();
        assertThat(service.verify(base32Secret, "000000", 0)).isFalse();
    }

    @Test
    void should_return_false_for_blank_invalid_or_unusable_inputs() {
        assertThat(service.verify(null, "123456", 1)).isFalse();
        assertThat(service.verify("   ", "123456", 1)).isFalse();
        assertThat(service.verify("JBSWY3DP", null, 1)).isFalse();
        assertThat(service.verify("JBSWY3DP", " ", 1)).isFalse();
        assertThat(service.verify("!!!!", "123456", 1)).isFalse();
        assertThat(service.verify("INVALID%%%%", "123456", 1)).isFalse();
    }
}
