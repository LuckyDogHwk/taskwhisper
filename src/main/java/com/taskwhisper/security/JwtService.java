package com.taskwhisper.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（Nimbus JOSE+JWT，来自 spring-security-oauth2-jose）。
 * HS256 对称签名；subject 存用户 id，附加 username 声明，过期时间 = now + expire-hours 小时。
 */
@Component
public class JwtService {

    private final String secret;
    private final long expireHours;

    public JwtService(@Value("${taskwhisper.jwt.secret}") String secret,
                      @Value("${taskwhisper.jwt.expire-hours}") long expireHours) {
        this.secret = secret;
        this.expireHours = expireHours;
    }

    /**
     * 签发 token：subject=userId 字符串，claims 含 username、签发时间、过期时间。
     */
    public String generateToken(Long userId, String username) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))
                    .claim("username", username)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + expireHours * 3_600_000L))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 签发失败", e);
        }
    }

    /**
     * 解析并校验 token，返回 subject 中的用户 id。
     * 格式错误、签名不符、已过期统一抛 IllegalArgumentException("无效的登录凭证")（同一文案）。
     */
    public Long parseUserId(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret.getBytes(StandardCharsets.UTF_8));
            if (!jwt.verify(verifier)) {
                throw new IllegalArgumentException("无效的登录凭证");
            }
            Date expirationTime = jwt.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                throw new IllegalArgumentException("无效的登录凭证");
            }
            return Long.parseLong(jwt.getJWTClaimsSet().getSubject());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的登录凭证");
        }
    }
}
