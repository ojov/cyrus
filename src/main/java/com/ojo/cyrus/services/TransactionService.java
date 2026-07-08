package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.responses.TransactionResponse;
import com.ojo.cyrus.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only, merchant-scoped access to the transaction ledger — the standalone Transactions API
 * (distinct from a customer's nested statement, which covers one customer at a time).
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(UUID merchantId, String customerReference, TransactionType type,
            TransactionStatus status, MatchStatus matchStatus, Instant from, Instant to, Pageable pageable) {

        // Same Specification approach as CustomerService.getStatement, for the same reason: Postgres
        // can't infer a bind parameter's type from a bare `? IS NULL` check, so a JPQL
        // `(:param IS NULL OR ...)` guard fails at runtime for a genuinely null filter value.
        // Specification just omits the predicate entirely for an absent filter.
        Specification<Transaction> spec = (root, query, cb) -> cb.equal(root.get("merchant").get("id"), merchantId);
        if (customerReference != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("customer").get("externalCustomerId"), customerReference));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (matchStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("matchStatus"), matchStatus));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("receivedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("receivedAt"), to));
        }

        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "receivedAt"));
        return transactionRepository.findAll(spec, sorted).map(TransactionService::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByReference(UUID merchantId, String reference) {
        Transaction tx = transactionRepository.findByReferenceAndMerchantId(reference, merchantId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
        return toResponse(tx);
    }

    private static TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getReference(),
                tx.getType(),
                tx.getCustomer() != null ? tx.getCustomer().getExternalCustomerId() : null,
                tx.getReceivedAt(),
                tx.getPayerName(),
                tx.getProviderTransactionId(),
                tx.getStatus(),
                tx.getMatchStatus(),
                tx.getAmount(),
                tx.getFee());
    }
}
