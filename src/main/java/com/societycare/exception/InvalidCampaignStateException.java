package com.societycare.exception;

import org.springframework.http.HttpStatus;

/** The campaign is in a state that does not allow the requested action (e.g. joining an EXPIRED campaign). */
public class InvalidCampaignStateException extends BusinessRuleException {
    public InvalidCampaignStateException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
