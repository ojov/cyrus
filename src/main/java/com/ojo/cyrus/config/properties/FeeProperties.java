package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.math.BigInteger;

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
 */
@ConfigurationProperties(prefix = "app.fees")
public record FeeProperties(

        @DefaultValue("1.5") BigDecimal inflowPercent,

        @DefaultValue("1500") BigInteger inflowMinKobo,

        @DefaultValue("22500") BigInteger inflowMaxKobo,

        @DefaultValue("3000") BigInteger payoutFlatFeeKobo

) {}
