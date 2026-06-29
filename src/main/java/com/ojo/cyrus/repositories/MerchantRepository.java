package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    boolean existsByBusinessEmail(String businessEmail);
    boolean existsByNombaParentAccountId(String nombaParentAccountId);
    java.util.Optional<Merchant> findByBusinessEmail(String businessEmail);
}