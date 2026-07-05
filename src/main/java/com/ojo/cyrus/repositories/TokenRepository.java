package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.TokenType;
import com.ojo.cyrus.models.entities.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {
    // Always scoped by type: a token value must never be validated against the wrong purpose.
    Optional<Token> findByTokenAndType(String token, TokenType type);

    List<Token> findByMerchantIdAndTypeAndUsedFalse(UUID merchantId, TokenType type);
}
