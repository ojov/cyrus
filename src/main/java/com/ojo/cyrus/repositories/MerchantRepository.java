package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Merchant findByMerchantId(UUID merchantId);
    Merchant findByNombaParentAccountId(String nombaParentAccountId);
    boolean existsByNombaParentAccountId(String nombaParentAccountId);
    Merchant findByNombaClientId(String nombaClientId);
    boolean existsByNombaClientId(String nombaClientId);

}