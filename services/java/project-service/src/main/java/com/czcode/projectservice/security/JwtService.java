package com.czcode.projectservice.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final String DEFAULT_SECRET = "change-this-secret-to-at-least-32-bytes-for-dev";
  private static final String DEFAULT_ACTIVE_KID = "v1";

  private final Map<String, JwtParser> parserByKid;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public JwtService(
      Environment environment,
      @Value("${auth.jwt.secret:" + DEFAULT_SECRET + "}") String secret,
      @Value("${auth.jwt.active-kid:" + DEFAULT_ACTIVE_KID + "}") String activeKid) {
    String normalizedActiveKid = normalizeKid(activeKid);

    Map<String, String> configuredKeys = Binder.get(environment)
        .bind("auth.jwt.keys", Bindable.mapOf(String.class, String.class))
        .orElseGet(Map::of);

    Map<String, SecretKey> signingKeys = loadSigningKeys(configuredKeys, secret, normalizedActiveKid);
    Map<String, JwtParser> parserMap = new LinkedHashMap<>();
    for (Map.Entry<String, SecretKey> entry : signingKeys.entrySet()) {
      parserMap.put(entry.getKey(), Jwts.parser().verifyWith(entry.getValue()).build());
    }
    this.parserByKid = Map.copyOf(parserMap);
  }

  public Claims parseAndValidate(String token) {
    String kid = extractKid(token);
    if (kid != null) {
      JwtParser parser = parserByKid.get(kid);
      if (parser == null) {
        throw new JwtException("unknown kid: " + kid);
      }
      return parser.parseSignedClaims(token).getPayload();
    }

    JwtException lastException = null;
    for (JwtParser parser : parserByKid.values()) {
      try {
        return parser.parseSignedClaims(token).getPayload();
      } catch (JwtException ex) {
        lastException = ex;
      }
    }
    if (lastException != null) {
      throw lastException;
    }
    throw new JwtException("no signing key configured");
  }

  private Map<String, SecretKey> loadSigningKeys(
      Map<String, String> configuredKeys,
      String secret,
      String activeKid) {
    Map<String, SecretKey> signingKeys = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuredKeys.entrySet()) {
      String kid = normalizeKid(entry.getKey());
      String keySecret = normalizeSecret(entry.getValue());
      signingKeys.put(kid, toSigningKey(keySecret));
    }

    String normalizedSecret = normalizeSecret(secret);
    if (normalizedSecret != null && !signingKeys.containsKey(activeKid)) {
      signingKeys.put(activeKid, toSigningKey(normalizedSecret));
    }

    if (signingKeys.isEmpty()) {
      throw new IllegalStateException("auth.jwt.keys or auth.jwt.secret must be configured");
    }
    return signingKeys;
  }

  private String extractKid(String token) {
    String[] segments = token.split("\\.");
    if (segments.length < 2) {
      throw new JwtException("invalid jwt token");
    }
    try {
      byte[] headerBytes = Base64.getUrlDecoder().decode(segments[0]);
      JsonNode headerNode = objectMapper.readTree(new String(headerBytes, StandardCharsets.UTF_8));
      String kid = headerNode.path("kid").asText(null);
      if (kid == null || kid.isBlank()) {
        return null;
      }
      return normalizeKid(kid);
    } catch (Exception ex) {
      throw new JwtException("invalid jwt header", ex);
    }
  }

  private SecretKey toSigningKey(String secret) {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("jwt secret must be at least 32 bytes");
    }
    return Keys.hmacShaKeyFor(keyBytes);
  }

  private String normalizeKid(String kid) {
    String normalized = kid == null ? "" : kid.trim();
    if (normalized.isEmpty()) {
      throw new IllegalStateException("auth.jwt.active-kid cannot be blank");
    }
    return normalized;
  }

  private String normalizeSecret(String secret) {
    if (secret == null) {
      return null;
    }
    String normalized = secret.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
