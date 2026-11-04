package com.innovax.ganadera.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;

import javax.crypto.SecretKey;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import com.innovax.ganadera.common.tenant.AuthenticatedUser;

/**
 * Genera y valida los tokens.
 *
 * <ul>
 *   <li><b>Access token</b> (vida corta): va en cada request como {@code Authorization: Bearer ...}.</li>
 *   <li><b>Refresh token</b> (vida larga): solo sirve para pedir tokens nuevos en {@code /api/auth/refresh}.</li>
 * </ul>
 * El claim {@code type} evita que se use uno en lugar del otro.
 */
@Service
public class TokenService {

    public static final String TIPO_ACCESS = "access";
    public static final String TIPO_REFRESH = "refresh";
    /** Sesión del panel de administración: el subject es el email y no lleva organización. */
    public static final String TIPO_ADMIN = "admin";

    static final String CLAIM_TIPO = "type";
    static final String CLAIM_ORGANIZACION = "org";
    static final String CLAIM_EMAIL = "email";

    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, SecretKey jwtSecretKey, JwtProperties properties) {
        this.encoder = encoder;
        this.refreshDecoder = decoderPara(jwtSecretKey, TIPO_REFRESH);
        this.properties = properties;
    }

    public record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    public Tokens generar(AuthenticatedUser usuario) {
        return new Tokens(
            firmar(usuario, TIPO_ACCESS, properties.accessTokenTtl()),
            firmar(usuario, TIPO_REFRESH, properties.refreshTokenTtl()),
            properties.accessTokenTtl().toSeconds());
    }

    /** Token del panel de administración (sin refresh: al vencer se vuelve a ingresar). */
    public String generarAdmin(String email, Duration duracion) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .subject(email)
            .issuedAt(ahora)
            .expiresAt(ahora.plus(duracion))
            .claim(CLAIM_TIPO, TIPO_ADMIN)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Si el token (ya validado) es del panel de administración. */
    public static boolean esAdmin(Jwt jwt) {
        return TIPO_ADMIN.equals(jwt.getClaimAsString(CLAIM_TIPO));
    }

    /**
     * Valida un refresh token y devuelve el id del usuario.
     *
     * @throws JwtException si el token es inválido, está vencido o no es de tipo refresh
     */
    public UUID validarRefreshToken(String token) {
        return UUID.fromString(refreshDecoder.decode(token).getSubject());
    }

    /**
     * Como {@link #validarRefreshToken}, pero devuelve el token entero (para saber cuándo se emitió).
     *
     * @throws JwtException si el token es inválido, está vencido o no es de tipo refresh
     */
    public Jwt decodificarRefreshToken(String token) {
        return refreshDecoder.decode(token);
    }

    /** Convierte un access token ya validado en el usuario logueado. */
    public static AuthenticatedUser usuarioDesde(Jwt jwt) {
        return new AuthenticatedUser(
            UUID.fromString(jwt.getSubject()),
            UUID.fromString(jwt.getClaimAsString(CLAIM_ORGANIZACION)),
            jwt.getClaimAsString(CLAIM_EMAIL));
    }

    private String firmar(AuthenticatedUser usuario, String tipo, Duration duracion) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .subject(usuario.userId().toString())
            .issuedAt(ahora)
            .expiresAt(ahora.plus(duracion))
            .claim(CLAIM_TIPO, tipo)
            .claim(CLAIM_ORGANIZACION, usuario.organizacionId().toString())
            .claim(CLAIM_EMAIL, usuario.email())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Decoder que valida firma, vencimiento y que el token sea del tipo indicado. */
    static JwtDecoder decoderPara(SecretKey key, String tipoEsperado) {
        return decoder(key, jwt -> esDeUsuario(jwt, tipoEsperado));
    }

    /**
     * Decoder de cada request: acepta el access token de un usuario o el del panel de
     * administración. Qué puede hacer cada uno lo decide {@code SecurityConfig} por rol.
     */
    static JwtDecoder decoderDeRequests(SecretKey key) {
        return decoder(key, jwt -> esDeUsuario(jwt, TIPO_ACCESS)
            || (TIPO_ADMIN.equals(jwt.getClaimAsString(CLAIM_TIPO)) && jwt.getSubject() != null));
    }

    private static boolean esDeUsuario(Jwt jwt, String tipo) {
        return tipo.equals(jwt.getClaimAsString(CLAIM_TIPO))
            && jwt.getSubject() != null
            && jwt.getClaimAsString(CLAIM_ORGANIZACION) != null;
    }

    private static JwtDecoder decoder(SecretKey key, Predicate<Jwt> valido) {
        NimbusJwtDecoder decoder = JwtConfig.baseDecoder(key);
        OAuth2TokenValidator<Jwt> tipoValidator = jwt -> valido.test(jwt)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Tipo de token inválido", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), tipoValidator));
        return decoder;
    }
}
