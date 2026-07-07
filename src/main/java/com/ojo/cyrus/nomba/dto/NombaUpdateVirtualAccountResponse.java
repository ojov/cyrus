package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NombaUpdateVirtualAccountResponse(
        @JsonProperty("updated") boolean updated
) {}
