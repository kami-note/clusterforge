package com.kryptforge.clusterforge.users;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clusterforge.jwt")
public class JwtProperties {

	/**
	 * Chave secreta para assinatura dos tokens JWT.
	 */
	private String secretKey;

	/**
	 * Tempo de expiração do token em milissegundos (padrão: 24 horas).
	 */
	private long expirationMs = 86400000; // 24 horas

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public long getExpirationMs() {
		return expirationMs;
	}

	public void setExpirationMs(long expirationMs) {
		this.expirationMs = expirationMs;
	}
}

