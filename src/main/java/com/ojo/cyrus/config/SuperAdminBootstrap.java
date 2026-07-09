package com.ojo.cyrus.config;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.enums.MerchantRole;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.repositories.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the bootstrap super-admin(s) at startup: any merchant whose email is in
 * {@code app.super-admin-emails} is promoted to {@link MerchantRole#SUPER_ADMIN}. This resolves the
 * chicken-and-egg of granting the first admin their role without an existing admin to do it.
 *
 * <p>Additive only — it never demotes. A merchant already SUPER_ADMIN (or one granted the role by
 * some future admin action) is left as-is, and removing an email from the list does not strip a
 * role. A listed email that doesn't match any merchant yet is simply skipped (they can be promoted
 * on a later restart once they've registered). Email match is case-insensitive.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final AppProperties appProperties;
    private final MerchantRepository merchantRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String rawEmail : appProperties.superAdminEmails()) {
            if (rawEmail == null || rawEmail.isBlank()) {
                continue;
            }
            String email = rawEmail.trim();
            Merchant merchant = merchantRepository.findByBusinessEmail(email)
                    .or(() -> merchantRepository.findByBusinessEmail(email.toLowerCase()))
                    .orElse(null);
            if (merchant == null) {
                log.info("Super-admin bootstrap: no merchant yet for {} — skipping (will retry on next restart)", email);
                continue;
            }
            if (merchant.getRole() == MerchantRole.SUPER_ADMIN) {
                continue; // already an admin; nothing to do
            }
            merchant.setRole(MerchantRole.SUPER_ADMIN);
            log.info("Super-admin bootstrap: promoted {} to SUPER_ADMIN", email);
        }
    }
}
