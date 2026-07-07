package com.ojo.cyrus.models.dto;

import com.ojo.cyrus.enums.ReconciliationOutcome;

public record RequeryApplication(ReconciliationOutcome outcome, int attempts) {}
