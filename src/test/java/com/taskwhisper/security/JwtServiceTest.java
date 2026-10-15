package com.taskwhisper.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtService 纯单元测试（不起 Spring）：roundtrip / 篡改 / 过期 / 格式错误。
 */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void roundTripParsesUserIdBack() {
        JwtService service = new JwtService(SECRET, 24);
        String token = service.generateToken(42L, "hwk");
        assertThat(service.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 24);
        String token = service.generateToken(42L, "hwk");
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');
        assertThatThrownBy(() -> service.parseUserId(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无效的登录凭证");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1);
        String token = service.generateToken(42L, "hwk");
        assertThatThrownBy(() -> service.parseUserId(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无效的登录凭证");
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 24);
        assertThatThrownBy(() -> service.parseUserId("not-a-jwt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无效的登录凭证");
    }
}
