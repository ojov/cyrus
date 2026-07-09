package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.MerchantCustomer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantCustomerRepository extends JpaRepository<MerchantCustomer, UUID> {

    boolean existsByMerchantIdAndExternalCustomerId(UUID merchantId, String externalCustomerId);

    Optional<MerchantCustomer> findByMerchantIdAndExternalCustomerId(UUID merchantId, String externalCustomerId);

    long countByMerchantId(UUID merchantId);

    // LEFT JOIN FETCH on a to-one association (virtualAccount is @OneToOne) is safe to paginate —
    // the in-memory-pagination pitfall only applies to a collection (@OneToMany/@ManyToMany) fetch
    // join. Spring Data derives the count query by stripping the fetch join automatically.
    @Query("SELECT mc FROM MerchantCustomer mc LEFT JOIN FETCH mc.virtualAccount WHERE mc.merchant.id = :merchantId")
    Page<MerchantCustomer> findByMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);
}
