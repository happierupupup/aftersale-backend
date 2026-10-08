package com.yean.gateway.jwt;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtService {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_ROLE = "X-Role";
    public static final String HEADER_IDENTITY_TYPE = "X-Identity-Type";
    public static final String HEADER_DEPT_ID = "X-Dept-Id";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_TYP = "typ";

    private final GatewayProperties properties;
    private final SecretKey key;

    public JwtService(GatewayProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccess(JwtUser user) {
        return issue(user, TYPE_ACCESS, accessExpireSeconds());
    }

    public String issueRefresh(JwtUser user) {
        return issue(user, TYPE_REFRESH, refreshExpireSeconds());
    }

    private String issue(JwtUser user, String typ, long expireSeconds) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .setId(jti)
                .setSubject(String.valueOf(user.getUserId()))
                .setIssuedAt(now)
                .setExpiration(exp)
                .claim("userId", user.getUserId())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim("identityType", user.getIdentityType() == null ? user.getRole() : user.getIdentityType())
                .claim("deptId", user.getDeptId())
                .claim(CLAIM_TYP, typ)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 业务鉴权：仅 Accept Access */
    public JwtUser parseAccess(String token) {
        JwtUser user = parse(token, false);
        requireType(user, TYPE_ACCESS);
        return user;
    }

    /** 续期：仅 Accept Refresh */
    public JwtUser parseRefresh(String token) {
        JwtUser user = parse(token, false);
        requireType(user, TYPE_REFRESH);
        return user;
    }

    /** 登出拉黑：允许已过期，仍取出 jti */
    public JwtUser parseIgnoreExpiry(String token) {
        return parse(token, true);
    }

    private JwtUser parse(String token, boolean ignoreExpiry) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return fromClaims(claims);
        } catch (ExpiredJwtException e) {
            if (ignoreExpiry && e.getClaims() != null) {
                return fromClaims(e.getClaims());
            }
            throw new BizException(ErrorEnum.TOKEN_EXPIRED);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorEnum.TOKEN_INVALID);
        }
    }

    private static void requireType(JwtUser user, String expected) {
        if (user.getTokenType() == null || !expected.equalsIgnoreCase(user.getTokenType())) {
            throw new BizException(ErrorEnum.TOKEN_INVALID.getCode(), "令牌类型不正确");
        }
    }

    private static JwtUser fromClaims(Claims claims) {
        return JwtUser.builder()
                .jti(claims.getId())
                .userId(asLong(claims.get("userId")))
                .username(claims.get("username", String.class))
                .role(claims.get("role", String.class))
                .identityType(claims.get("identityType", String.class))
                .deptId(asLong(claims.get("deptId")))
                .tokenType(claims.get(CLAIM_TYP, String.class))
                .build();
    }

    public Duration remainingTtl(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            Date exp = claims.getExpiration();
            long millis = exp.getTime() - System.currentTimeMillis();
            return millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO;
        } catch (ExpiredJwtException e) {
            return Duration.ZERO;
        } catch (Exception e) {
            throw new BizException(ErrorEnum.TOKEN_INVALID);
        }
    }

    public long accessExpireSeconds() {
        return properties.getJwt().getAccessExpireSeconds();
    }

    public long refreshExpireSeconds() {
        return properties.getJwt().getRefreshExpireSeconds();
    }

    public static Optional<String> bearer(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
