package com.societycare.enums;

/**
 * Campaign lifecycle:
 * PENDING -> CONFIRMED (automatic, when minimum participants is reached)
 * PENDING -> EXPIRED   (automatic, when the end date passes first)
 * CONFIRMED -> COMPLETED (provider or admin, after the service is delivered)
 * EXPIRED and COMPLETED are final states.
 */
public enum CampaignStatus {
    PENDING,
    CONFIRMED,
    EXPIRED,
    COMPLETED;

    public boolean canTransitionTo(CampaignStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == EXPIRED;
            case CONFIRMED -> target == COMPLETED;
            case EXPIRED, COMPLETED -> false;
        };
    }
}
