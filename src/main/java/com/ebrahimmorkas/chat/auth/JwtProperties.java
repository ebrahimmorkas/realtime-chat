package com.ebrahimmorkas.chat.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration) {
}
