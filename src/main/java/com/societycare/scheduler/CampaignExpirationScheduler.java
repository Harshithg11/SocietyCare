package com.societycare.scheduler;

import com.societycare.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires overdue PENDING campaigns automatically. Runs on the cron in app.campaign.expiration-cron
 * (default: every day at 01:00 server time). @EnableScheduling is in ApplicationConfig.
 * As a second safety net, joining an overdue campaign also expires it, and an ADMIN can trigger the same logic
 * on demand with POST /api/campaigns/expire-overdue.
 */
@Component
@RequiredArgsConstructor
public class CampaignExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CampaignExpirationScheduler.class);

    private final CampaignService campaignService;

    @Scheduled(cron = "${app.campaign.expiration-cron}")
    public void expireOverdueCampaigns() {
        int expired = campaignService.expireOverdueCampaigns();
        if (expired > 0) {
            log.info("Expired {} overdue PENDING campaign(s).", expired);
        }
    }
}
