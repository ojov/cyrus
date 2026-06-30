package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.CreateVirtualAccountRequest;
import com.ojo.cyrus.models.responses.VirtualAccountResponse;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import com.ojo.cyrus.utils.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class VirtualAccountService {

    private final VirtualAccountRepository repository;


    public VirtualAccountResponse create(
            Merchant merchant,
            CreateVirtualAccountRequest request
    ){

        if(repository.existsByMerchantIdAndCustomerReference(
                merchant.getId(),
                request.customerReference()
        )){
            throw new AlreadyExistsException(
                    "Virtual account already exists"
            );
        }


        // temporary until Nomba integration

        VirtualAccount account =
                VirtualAccount.builder()
                        .merchant(merchant)
                        .customerReference(
                                request.customerReference()
                        )
                        .customerName(
                                request.customerName()
                        )
                        .customerEmail(
                                request.customerEmail()
                        )
                        .accountNumber(
                                generateAccountNumber()
                        )
                        .accountName(
                                request.customerName()
                        )
                        .bankName(
                                "Nomba"
                        )
                        .status(
                                VirtualAccountStatus.ACTIVE
                        )
                        .build();


        return Mapper.mapToVirtualAccountResponse(repository.save(account));
    }


    private String generateAccountNumber(){
        return "29" +
                ThreadLocalRandom.current()
                        .nextInt(10000000,99999999);
    }
}