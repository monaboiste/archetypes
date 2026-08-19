package com.softwarearchetypes.scoring.customer;

import java.time.Instant;

public record CustomerEvent(String type, Instant occurredAt, double amount) {
}
