package com.ojo.cyrus.config.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Cyrus's revenue model. Inbound payments are charged as a percentage of the gross amount with
 * min/max caps; payouts are a flat fee per transfer.
 *
 * <p>Inflow: merchant pays {@code inflowPercent} of the payment (min {@code inflowMinKobo},
 * max {@code inflowMaxKobo}). Nomba's own fee (1% min ₦10 max ₦150) is tracked separately;
 * Cyrus's margin = merchant fee − Nomba fee.
 *
 * <p>Payout: merchant pays a flat {@code payoutFlatFeeKobo} per transfer. Nomba's ₦20 fixed
 * charge is deducted from the transfer; Cyrus keeps the remainder.
 *
 * <p>Initially bound from {@code application.yaml} defaults, then overwritten by
 * {@link com.ojo.cyrus.services.FeeConfigService} from the database on startup and on
 * every super-admin update. Mutable so the cached instance can be updated in place.
 */
@ConfigurationProperties(prefix = "app.fees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeeProperties {

    private BigDecimal inflowPercent = new BigDecimal("1.5");

    private BigDecimal inflowMinKobo = new BigDecimal("1500");

    private BigDecimal inflowMaxKobo = new BigDecimal("22500");

    private BigDecimal payoutFlatFeeKobo = new BigDecimal("3000");
}
