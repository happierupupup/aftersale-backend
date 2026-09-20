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

    private final GatewayProperties properties;
    private final SecretKey key;

    public JwtService(GatewayProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(JwtUser user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + properties.getJwt().getExpireSeconds() * 1000);
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
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public JwtUser parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return JwtUser.builder()
                    .jti(claims.getId())
                    .userId(asLong(claims.get("userId")))
                    .username(claims.get("username", String.class))
                    .role(claims.get("role", String.class))
                    .identityType(claims.get("identityType", String.class))
                    .deptId(asLong(claims.get("deptId")))
                    .build();
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorEnum.TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new BizException(ErrorEnum.TOKEN_INVALID);
        }
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

    public long expireSeconds() {
        return properties.getJwt().getExpireSeconds();
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
