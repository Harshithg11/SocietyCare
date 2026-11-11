package com.societycare.exception;

/**
 * Thrown when a join attempt finds that the campaign's end date has passed. The join transaction is configured
 * with noRollbackFor this exception so the EXPIRED status is still saved even though the request is rejected.
 */
public class CampaignExpiredException extends InvalidCampaignStateException {
    public CampaignExpiredException(String message) {
        super(message);
    }
}
