package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<ApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);
}
