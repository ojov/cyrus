package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.repositories.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchantUserDetailsService implements UserDetailsService {

    private final MerchantRepository merchantRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Merchant merchant = merchantRepository.findByBusinessEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for: " + email));

        return User.builder()
                .username(merchant.getBusinessEmail())
                .password(merchant.getPasswordHash())
                .disabled(merchant.getStatus() != MerchantStatus.ACTIVE)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")))
                .build();
    }
}
