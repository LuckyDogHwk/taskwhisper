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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证：注册与登录。密码只存 BCrypt 哈希；登录失败统一文案，防用户名枚举。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 注册：用户名已存在抛 CONFLICT；成功则 BCrypt 加密入库并签发 token。
     */
    public AuthResponse register(RegisterRequest req) {
        boolean exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.username())) > 0;
        if (exists) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userMapper.insert(user);
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername());
    }

    /**
     * 登录：用户不存在与密码不匹配返回同一文案的 UNAUTHORIZED（防用户枚举）；成功签发 token。
     */
    public AuthResponse login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername());
    }
}
