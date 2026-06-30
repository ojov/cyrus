package com.ojo.cyrus.utils;

import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.NombaCredential;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import com.ojo.cyrus.models.responses.VirtualAccountResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class Mapper {

    public static VirtualAccountResponse mapToVirtualAccountResponse(VirtualAccount account) {
        return new VirtualAccountResponse(
                account.getId(),
                account.getCustomerReference(),
                account.getAccountNumber(),
                account.getAccountName(),
                account.getBankName(),
                account.getStatus().name()
        );
    }

    public static Merchant mapToMerchantEntity(MerchantRegistrationRequest request) {
        Merchant merchant = Merchant.builder()
                .businessName(request.businessName())
                .businessEmail(request.businessEmail())
                .passwordHash((request.password()))
                .nombaParentAccountId(request.nombaParentAccountId())
                .nombaSubAccountIds(request.subAccountIds())
                .status(MerchantStatus.PENDING_VERIFICATION)
                .build();

        if (request.nombaClientId() != null) {
            merchant.getNombaCredentials().put(Environment.TEST,
                    new NombaCredential(request.nombaClientId(), request.nombaClientSecret()));
        }

        return merchant;
    }

}
