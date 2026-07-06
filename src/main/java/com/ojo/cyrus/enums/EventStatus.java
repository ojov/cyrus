package com.ojo.cyrus.enums;

public enum EventStatus {
    PENDING,
    PROCESSED,
    FAILED,
    IGNORED,
    PROCESSED_DUPLICATE,
    // An IGNORED (orphan/misdirected) event that a merchant manually reattributed to one of their
    // own customers, minting a Transaction retroactively.
    REATTRIBUTED
}
