package com.ojo.cyrus.models;
import jakarta.persistence.Embeddable;

@Embeddable
public record NombaCredential (String clientId, String encryptedClientSecret) {}
