package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.FeeProperties;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.FeeConfig;
import com.ojo.cyrus.models.requests.UpdateFeeConfigRequest;
import com.ojo.cyrus.models.responses.FeeConfigResponse;
import com.ojo.cyrus.repositories.FeeConfigRepository;
import com.ojo.cyrus.utils.MoneyUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the platform-wide fee configuration backed by a single-row {@link FeeConfig} table.
 * At startup, loads the DB row into an immutable {@link FeeProperties} snapshot so that
 * {@link com.ojo.cyrus.utils.FeeCalculator} and the services consuming {@link #current()}
 * see the persisted values without any code changes. On every admin update, a brand new
 * snapshot is swapped in atomically — {@link ReconciliationService} and {@link PayoutService}
 * run reconciliation on async/scheduled threads concurrently with admin updates, so readers must
 * never observe a torn mix of old/new fields (e.g. a new {@code inflowPercent} paired with the old
 * {@code inflowMaxKobo}). An {@link AtomicReference} swap of a whole new instance, rather than
 * mutating shared fields in place, gives both atomicity across the 4 fields and cross-thread
 * visibility (mutating a shared bean's individual fields via plain setters gives neither).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeConfigService {

    private final FeeConfigRepository feeConfigRepository;
    private final FeeProperties defaultFeeProperties;

    private final AtomicReference<FeeProperties> current = new AtomicReference<>();

    /** The current fee configuration as an immutable snapshot, safe to read from any thread. */
    public FeeProperties current() {
        return current.get();
    }

    @PostConstruct
    void init() {
        FeeProperties loaded = feeConfigRepository.findFirstByOrderByIdAsc()
                .map(FeeConfigService::toSnapshot)
                .orElse(defaultFeeProperties);
        current.set(loaded);
        log.info("Fee config loaded: inflow={}%, min={}, max={}, payout={}",
                loaded.getInflowPercent(), loaded.getInflowMinKobo(),
                loaded.getInflowMaxKobo(), loaded.getPayoutFlatFeeKobo());
    }

    @Transactional(readOnly = true)
    public FeeConfigResponse get() {
        FeeConfig config = feeConfigRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new EntityNotFoundException("Fee configuration not found"));
        return toResponse(config);
    }

    @Transactional
    public FeeConfigResponse update(UpdateFeeConfigRequest request) {
        validate(request);

        FeeConfig config = feeConfigRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new EntityNotFoundException("Fee configuration not found"));

        config.setInflowPercent(request.inflowPercent());
        config.setInflowMinKobo(MoneyUtil.normalize(request.inflowMinKobo()));
        config.setInflowMaxKobo(MoneyUtil.normalize(request.inflowMaxKobo()));
        config.setPayoutFlatFeeKobo(MoneyUtil.normalize(request.payoutFlatFeeKobo()));

        FeeConfig saved = feeConfigRepository.save(config);
        current.set(toSnapshot(saved));

        log.info("Fee config updated: inflow={}%, min={}, max={}, payout={}",
                saved.getInflowPercent(), saved.getInflowMinKobo(),
                saved.getInflowMaxKobo(), saved.getPayoutFlatFeeKobo());
        return toResponse(saved);
    }

    private static FeeProperties toSnapshot(FeeConfig config) {
        return new FeeProperties(config.getInflowPercent(), config.getInflowMinKobo(),
                config.getInflowMaxKobo(), config.getPayoutFlatFeeKobo());
    }

    private void validate(UpdateFeeConfigRequest request) {
        if (request.inflowMinKobo().compareTo(request.inflowMaxKobo()) > 0) {
            throw new IllegalArgumentException("inflowMinKobo must not exceed inflowMaxKobo");
        }
    }

    private static FeeConfigResponse toResponse(FeeConfig config) {
        return new FeeConfigResponse(
                config.getInflowPercent(),
                config.getInflowMinKobo(),
                config.getInflowMaxKobo(),
                config.getPayoutFlatFeeKobo(),
                config.getUpdatedAt());
    }
}
