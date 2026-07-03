package com.ojo.cyrus.nomba;

import com.ojo.cyrus.models.entities.Merchant;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;

@Component
public class CredentialMapper {

    /**
     * Must be called within an active persistence context: copying the lazy
     * {@code @ElementCollection}s into plain collections forces them to initialize, so the
     * returned {@link NombaCredentials} is safe to use after the transaction closes (e.g.
     * during the external Nomba call, which runs outside any DB transaction).
     */
    public NombaCredentials fromMerchant(Merchant merchant) {
        return new NombaCredentials(
                merchant.getId().toString(),
                merchant.getNombaParentAccountId(),
                new HashMap<>(merchant.getNombaCredentials()),
                new HashSet<>(merchant.getNombaSubAccountIds())
        );
    }
}
