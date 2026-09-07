package com.microfi.authentication.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    /**
     * The secret this repository ships for local development. It is committed here and in
     * kong/kong.yml, so it is public: any build still signing with it can have admin tokens
     * forged by anyone who has read the repo. Kept as a constant only so startup can recognise
     * and complain about it.
     */
    static final String PUBLISHED_DEV_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    /** Claim distinguishing which UserDetails service should resolve the token's subject. */
    public static final String PRINCIPAL_TYPE_CLAIM = "principalType";
    public static final String PRINCIPAL_TYPE_AGENT = "AGENT";
    public static final String PRINCIPAL_TYPE_ADMIN_USER = "ADMIN_USER";
    public static final String PRINCIPAL_TYPE_CLIENT = "CLIENT";

    /**
     * Matched against the Kong Gateway JWT plugin's consumer credential {@code key} — Kong looks
     * up which consumer's secret to verify against by this claim, so it must be present and
     * stable. See {@code kong/kong.yml}.
     */
    public static final String ISSUER = "microfi-core";

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    /**
     * Refuses to start rather than fall back to a signing key nobody chose. Every token this
     * service issues, and every token Kong accepts, rests on this one value, so an unset variable
     * is not a condition to recover from at runtime -- it has to surface at deploy time.
     */
    @PostConstruct
    void validateSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "MICROFI_JWT_SECRET is not set. Core signs and verifies every JWT with it, and "
                            + "it must be byte-identical to the consumer secret in kong/kong.yml.");
        }
        if (PUBLISHED_DEV_SECRET.equals(secretKey)) {
            log.warn("MICROFI_JWT_SECRET is the development secret committed to this repository. "
                    + "It is public: anyone who can read the repo can forge an ADMIN_USER token. "
                    + "Set a generated secret (and match it in kong/kong.yml) before this is reachable "
                    + "by anyone outside your machine.");
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractPrincipalType(String token) {
        return extractClaim(token, claims -> claims.get(PRINCIPAL_TYPE_CLAIM, String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails
    ) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .issuer(ISSUER)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                // Explicit algorithm, not left to jjwt's key-length auto-selection: Kong's JWT
                // plugin credential pins a specific algorithm (HS384, see kong/kong.yml) and
                // rejects a token signed with any other one, so this must stay deterministic
                // regardless of how long application.security.jwt.secret-key happens to be.
                .signWith(getSignInKey(), Jwts.SIG.HS384)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && userDetails.isEnabled()
                && userDetails.isAccountNonLocked();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Raw UTF-8 bytes of the configured secret, not base64-decoded — matches how Kong's JWT
     * plugin derives HMAC key material from its consumer credential {@code secret} field, so the
     * same configured string verifies identically at the Gateway and here. (Previously
     * base64-decoded; changing this invalidates any already-issued tokens, which is fine given
     * their short lifetime — see kong/kong.yml.)
     */
    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
