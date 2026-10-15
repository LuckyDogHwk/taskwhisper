package com.taskwhisper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import com.taskwhisper.dto.AuthResponse;
import com.taskwhisper.dto.LoginRequest;
import com.taskwhisper.dto.RegisterRequest;
import com.taskwhisper.entity.User;
import com.taskwhisper.mapper.UserMapper;
import com.taskwhisper.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AuthService 集成测试：真实 H2 + 真实 BCrypt + 真实 Mapper，仅 mock JWT 签发。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void registerThenLoginRoundTripReturnsUserIdAndUsername() {
        when(jwtService.generateToken(anyLong(), anyString())).thenReturn("mock-token");

        AuthResponse registered = authService.register(new RegisterRequest("roundtrip_user", "secret123"));
        assertThat(registered.userId()).isNotNull();
        assertThat(registered.username()).isEqualTo("roundtrip_user");
        assertThat(registered.token()).isEqualTo("mock-token");

        AuthResponse loggedIn = authService.login(new LoginRequest("roundtrip_user", "secret123"));
        assertThat(loggedIn.userId()).isEqualTo(registered.userId());
        assertThat(loggedIn.username()).isEqualTo("roundtrip_user");
    }

    @Test
    void duplicateRegisterThrowsConflict409() {
        authService.register(new RegisterRequest("dup_user", "secret123"));
        assertThatThrownBy(() -> authService.register(new RegisterRequest("dup_user", "another456")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void wrongPasswordThrows401() {
        authService.register(new RegisterRequest("wrong_pw_user", "secret123"));
        assertThatThrownBy(() -> authService.login(new LoginRequest("wrong_pw_user", "bad-password")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo("用户名或密码错误");
                });
    }

    @Test
    void unknownUserThrows401WithSameMessageAsWrongPassword() {
        authService.register(new RegisterRequest("existing_user", "secret123"));
        BizException wrongPassword = catchThrowableOfType(
                () -> authService.login(new LoginRequest("existing_user", "bad-password")), BizException.class);
        BizException unknownUser = catchThrowableOfType(
                () -> authService.login(new LoginRequest("no_such_user", "whatever1")), BizException.class);
        assertThat(unknownUser).isNotNull();
        assertThat(unknownUser.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(unknownUser.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    void passwordStoredAsBcryptHashNotPlainText() {
        authService.register(new RegisterRequest("hash_user", "secret123"));
        User stored = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "hash_user"));
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", stored.getPasswordHash())).isTrue();
    }
}
