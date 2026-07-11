package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.*;

import java.math.BigDecimal;

/**
 * Single-row table holding Cyrus's platform-wide fee configuration. Managed exclusively by
 * super-admins via {@link com.ojo.cyrus.controllers.dashboard.PlatformFeeConfigController}.
 * Loaded into {@link com.ojo.cyrus.config.properties.FeeProperties} at startup and refreshed
 * on every update so that {@link com.ojo.cyrus.utils.FeeCalculator} and the services that
 * consume {@code FeeProperties} always see the latest values without redeployment.
 */
@Entity
@Table(name = "fee_config")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FeeConfig extends BaseEntity {

    /** Inbound payment fee as a percentage of the gross amount (e.g. 1.5 = 1.5%). */
    @Column(nullable = false)
    private BigDecimal inflowPercent;

    /** Minimum inbound fee in kobo (clamped floor). */
    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal inflowMinKobo;

    /** Maximum inbound fee in kobo (clamped ceiling). */
    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal inflowMaxKobo;

    /** Flat fee per outbound payout transfer in kobo. */
    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal payoutFlatFeeKobo;

    @Version
    private Long version;
}
