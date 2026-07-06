package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    boolean existsByBusinessEmail(String businessEmail);
    boolean existsByNombaParentAccountId(String nombaParentAccountId);
    Optional<Merchant> findByBusinessEmail(String businessEmail);

    // Resolves the merchant owning a given Nomba wallet — a VA transfer's webhook carries the
    // sub-account wallet it landed in (merchant.walletId), not necessarily the parent account, so
    // this checks both. Used to identify the merchant for a payment before/independent of any
    // virtual-account lookup (critical for orphan/misdirected payments, where VA lookup fails).
    @Query("""
            SELECT m FROM Merchant m
            WHERE m.nombaParentAccountId = :walletId OR :walletId MEMBER OF m.nombaSubAccountIds
            """)
    Optional<Merchant> findByNombaWalletId(@Param("walletId") String walletId);
}