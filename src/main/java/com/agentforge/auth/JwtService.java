package com.agentforge.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.agentforge.user.Role;

@Service
public class JwtService {

	private final JwtEncoder jwtEncoder;
	private final String issuer;
	private final Duration ttl;

	public JwtService(SecretKey jwtSecretKey, @Value("${agentforge.jwt.issuer}") String issuer,
			@Value("${agentforge.jwt.ttl}") String ttlProperty) {
		this.jwtEncoder = NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
		this.issuer = issuer;
		this.ttl = DurationStyle.detectAndParse(ttlProperty);
	}

	public Jwt issueToken(UUID userId, Role role, UUID tenantId) {
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.subject(userId.toString())
			.claim("role", role.name())
			.issuedAt(now)
			.expiresAt(now.plus(ttl));

		// Platform admin has no tenant - the claim must be absent, not present with a null value
		if (tenantId != null) {
			claims.claim("tenantId", tenantId.toString());
		}

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build()));
	}

}
