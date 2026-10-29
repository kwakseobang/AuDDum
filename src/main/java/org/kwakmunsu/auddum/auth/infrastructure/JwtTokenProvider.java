package org.kwakmunsu.auddum.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·검증. 액세스는 {sub, category, role, iat, exp}, 리프레시는 {sub, category, jti, iat, exp}를 담는다.
 * 두 토큰은 {@code category} 클레임(access, refresh)으로 구분하고, 다른 종류의 토큰은 받아주지 않는다.
 * 시각은 시스템 시계가 아니라 호출자가 넘긴 now를 기준으로 한다. 서명이 틀리거나 만료됐거나 필요한 값이 없으면 UNAUTHENTICATED다.
 */
@Slf4j
@EnableConfigurationProperties(JwtProperties.class)
@Component
public class JwtTokenProvider {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    private static final String ROLE = "role";
    private static final String CATEGORY = "category";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId, Role role, LocalDateTime now) {
        return Jwts.builder()
            .subject(String.valueOf(memberId))
            .claim(CATEGORY, ACCESS)
            .claim(ROLE, role.name())
            .issuedAt(toDate(now))
            .expiration(toDate(now.plus(ACCESS_TTL)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public String createRefreshToken(Long memberId, LocalDateTime now) {
        return Jwts.builder()
            .subject(String.valueOf(memberId))
            .claim(CATEGORY, REFRESH)
            .id(UUID.randomUUID().toString())
            .issuedAt(toDate(now))
            .expiration(toDate(now.plus(REFRESH_TTL)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public AccessClaims parseAccessToken(String token, LocalDateTime now) {
        Claims claims = parse(token, now, ACCESS);
        String role = claims.get(ROLE, String.class);
        if (role == null) {
            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
        }

        try {
            return new AccessClaims(Long.valueOf(claims.getSubject()), Role.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED, e);
        }
    }

    public RefreshClaims parseRefreshToken(String token, LocalDateTime now) {
        Claims claims = parse(token, now, REFRESH);
        if (claims.getId() == null) {
            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
        }

        try {
            return new RefreshClaims(Long.valueOf(claims.getSubject()), claims.getId());
        } catch (NumberFormatException e) {
            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED, e);
        }
    }

    private Claims parse(String token, LocalDateTime now, String category) {
        Claims claims = parse(token, now);
        if (!category.equals(claims.get(CATEGORY, String.class))) {
            log.warn("[JwtTokenProvider] 토큰 종류 불일치 - expected={}, token={}", category, mask(token));

            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
        }

        return claims;
    }

    private Claims parse(String token, LocalDateTime now) {
        try {
            return Jwts.parser()
                .verifyWith(key)
                .clock(() -> toDate(now))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            log.info("[JwtTokenProvider] 만료된 토큰 - token={}", mask(token));

            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED, e);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JwtTokenProvider] 유효하지 않은 토큰 - reason={}, token={}", e.getClass().getSimpleName(), mask(token));

            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED, e);
        }
    }

    /** 로그에 토큰이 통째로 남지 않게 앞뒤 일부만 보인다. */
    private String mask(String token) {
        if (token == null || token.length() < 20) {
            return "***";
        }

        return token.substring(0, 10) + "..." + token.substring(token.length() - 5);
    }

    private Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(TimeConfig.KST).toInstant());
    }

    public record AccessClaims(Long memberId, Role role) {

    }

    public record RefreshClaims(Long memberId, String jti) {

    }

}
