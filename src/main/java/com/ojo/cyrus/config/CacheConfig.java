package com.ojo.cyrus.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the cache abstraction with an explicit in-memory {@link ConcurrentMapCacheManager}.
 *
 * <p>The bean is defined explicitly rather than relying on Spring Boot's cache auto-configuration:
 * with {@code @EnableCaching} but no {@code CacheManager} bean, the context fails to start
 * ("No qualifying bean of type 'org.springframework.cache.CacheManager'"). Declaring it here
 * removes any dependence on auto-config ordering/back-off. Cache names are pre-declared so a typo in
 * a {@code @Cacheable("...")} name surfaces as an unknown cache rather than silently creating one.
 *
 * <p>{@code nombaBanks} — the effectively-static Nomba bank list (see
 * {@code NombaTransferClient.listBanks()}). In-memory and per-instance, cleared on restart; fine for
 * a static list.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("nombaBanks");
    }
}
