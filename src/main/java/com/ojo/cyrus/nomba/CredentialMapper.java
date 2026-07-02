package com.ojo.cyrus.nomba;

import com.ojo.cyrus.models.entities.Merchant;
import org.springframework.stereotype.Component;

@Component
public class CredentialMapper {

    public NombaCredentials fromMerchant(Merchant merchant) {
        return new NombaCredentials(
                merchant.getId().toString(),
                merchant.getNombaParentAccountId(),
                merchant.getNombaCredentials(),
                merchant.getNombaSubAccountIds()
        );
    }
}
