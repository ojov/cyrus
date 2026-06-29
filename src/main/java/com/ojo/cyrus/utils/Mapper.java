package com.ojo.cyrus.utils;

import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Mapper {
    public static MerchantRegistrationResponse merchantToRegistrationResponse(Merchant merchant, String apiKey) {
        return new MerchantRegistrationResponse(merchant.getId(), apiKey);
    }

}
