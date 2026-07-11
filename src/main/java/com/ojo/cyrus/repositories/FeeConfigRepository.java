package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.FeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeeConfigRepository extends JpaRepository<FeeConfig, UUID> {

    /** The single row — empty only on a brand-new DB before the seed migration runs. */
    Optional<FeeConfig> findFirstByOrderByIdAsc();
}
