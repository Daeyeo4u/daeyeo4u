package com.main10.global.security.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main10.global.exception.ExceptionCode;
import com.main10.domain.member.entity.Member;
import com.main10.global.security.exception.AuthException;
import com.main10.global.security.vo.ClaimsVO;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

/**
 * 토큰 발급용 Util 클래스
 *
 * @author mozzi327
 */
@Slf4j
@Component
public class JwtTokenUtils {

    @Getter
    private final String secretKey;

    @Getter
    private final int accessTokenExpirationMinutes;

    @Getter
    private final int refreshTokenExpirationMinutes;

    private final ObjectMapper objectMapper;

    public JwtTokenUtils(@Value("${jwt.secret-key}") String secretKey,
                         @Value("${jwt.access-token-expiration-minutes}") int accessTokenExpirationMinutes,
                         @Value("${jwt.refresh-token-expiration-minutes}") int refreshTokenExpirationMinutes,
                         ObjectMapper objectMapper) {
        this.secretKey = secretKey;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationMinutes = refreshTokenExpirationMinutes;
        this.objectMapper = objectMapper;
    }

    /**
     * 서버 환경변수에 저장된 시크릿 키를 인코딩하여 반환해주는 메서드
     *
     * @param secretKey 시크릿 키
     * @return 인코딩된 시크릿 키
     * @author mozzi327
     */
    public String encodeBase64SecretKey(String secretKey) {
        return Encoders.BASE64.encode(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 엑세스 토큰을 발급하는 메서드
     *
     * @param member 사용자 정보
     * @return String(액세스 토큰)
     * @author mozzi327
     */
    public String generateAccessToken(Member member) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", member.getEmail());
        claims.put("roles", member.getRoles());
        claims.put("id", member.getId());

        String subject = member.getEmail();
        Date expiration = getTokenExpiration(getAccessTokenExpirationMinutes());
        String base64EncodedSecretKey = encodeBase64SecretKey(getSecretKey());

        Key key = getKeyFromBase64EncodedKey(base64EncodedSecretKey);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Calendar.getInstance().getTime())
                .setExpiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 리프레시 토큰을 발급하는 메서드
     *
     * @return String(리프레시 토큰)
     * @author mozzi327
     */
    public String generateRefreshToken(Member member) {
        String subject = member.getEmail();
        Date expiration = getTokenExpiration(getRefreshTokenExpirationMinutes());
        String base64EncodedSecretKey = encodeBase64SecretKey(getSecretKey());

        Key key = getKeyFromBase64EncodedKey(base64EncodedSecretKey);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Calendar.getInstance().getTime())
                .setExpiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 검증 후 Jws(Claims) 정보를 반환해주는 메서드
     *
     * @param jws 시그니처 정보
     * @return Map
     * @author mozzi327
     */
    public Map<String, Object> getClaims(String jws) {
        String base64EncodedSecretKey = encodeBase64SecretKey(getSecretKey());
        Key key = getKeyFromBase64EncodedKey(base64EncodedSecretKey);

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build().parseClaimsJws(jws).getBody();
    }

    /**
     * 액세스 토큰의 만료시간을 반환해주는 메서드
     *
     * @param expirationMinutes 서버 지정 액세스 토큰 만료 시간
     * @return Date
     * @author mozzi327
     */
    public Date getTokenExpiration(int expirationMinutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, expirationMinutes);
        return calendar.getTime();
    }

    /**
     * 액세스 토큰을 통해 만료 시간을 계산해주는 메서드
     *
     * @param accessToken 액세스 토큰
     * @return Long
     * @author mozzi327
     */
    public Long getExpiration(String accessToken) {
        Key key = getKeyFromBase64EncodedKey(encodeBase64SecretKey(secretKey));
        // accessToken 남은 유효시간
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(accessToken)
                .getBody()
                .getExpiration();
        // 현재 시간
        long now = new Date().getTime();
        return (expiration.getTime() - now);
    }

    /**
     * base64로 인코딩된 키를 Key 객체로 만들어 반환하는 메서드
     *
     * @param base64EncodedSecretKey base64 인코딩된 키
     * @return Key
     * @author mozzi327
     */
    private Key getKeyFromBase64EncodedKey(String base64EncodedSecretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(base64EncodedSecretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 토큰 정보를 검증하는 메서드
     *
     * @param token 토큰 정보
     * @return boolean
     * @author mozzi327
     */
    public boolean validateToken(String token) {
        String base64EncodedSecretKey = encodeBase64SecretKey(getSecretKey());
        Key key = getKeyFromBase64EncodedKey(base64EncodedSecretKey);

        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return false;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT Token", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT Token", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty.", e);
        }
        return true;
    }

    /**
     * 액세스 토큰의 Prefix(Bearer )를 제거해주는 메서드
     *
     * @param accessToken 액세스 토큰
     * @return String(액세스 토큰)
     * @author mozzi327
     */
    public String parseAccessToken(String accessToken) {
        if (accessToken.startsWith(AuthConstants.BEARER))
            return accessToken.split(" ")[1];
        throw new AuthException(ExceptionCode.INVALID_AUTH_TOKEN);
    }

    /**
     * accessToken 파싱 후 이메일 출력
     *
     * @param token
     * @return 사용자 email
     * @author mozzi327
     */
    @SneakyThrows
    public ClaimsVO parseClaims(String token) {
        final String encodedPayload = token.split("\\.")[1].replace('-', '+').replace('_', '/');
        final String decoded = new String(Base64.getDecoder().decode(encodedPayload));
        return objectMapper.readValue(decoded, ClaimsVO.class);
    }

    /**
     * ClaimsVO를 통한 액세스 토큰 발급 메서드
     * @param claimsVO 파싱된 Claims 정보
     * @return String
     * @author mozzi327
     */
    public String generateAccessTokenByClaimsVO(ClaimsVO claimsVO) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", claimsVO.getId());
        claims.put("name", claimsVO.getName());
        claims.put("roles", claimsVO.getRoles());

        String subject = claimsVO.getSub();
        Date expiration = getTokenExpiration(getAccessTokenExpirationMinutes());
        String base64EncodedSecretKey = encodeBase64SecretKey(getSecretKey());

        Key key = getKeyFromBase64EncodedKey(base64EncodedSecretKey);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Calendar.getInstance().getTime())
                .setExpiration(expiration)
                .signWith(key)
                .compact();
    }
}