package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.NombaCredential;

import java.util.Map;
import java.util.Set;

public record NombaCredentials(
        String cacheKey,
        String parentAccountId,
        Map<Environment, NombaCredential> credentials,
        Set<String> subAccountIds
) {}
