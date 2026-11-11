package com.societycare.config;

import com.societycare.entity.BookingCampaign;
import com.societycare.entity.ServiceOffering;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.CampaignStatus;
import com.societycare.enums.Role;
import com.societycare.enums.ServiceCategory;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.ServiceOfferingRepository;
import com.societycare.repository.SocietyRepository;
import com.societycare.repository.UserRepository;
import com.societycare.util.PriceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DEVELOPMENT sample data. Only runs when app.seed.enabled=true (the local default).
 * Turn it off with the environment variable SEED_ENABLED=false (or app.seed.enabled=false in application.yml).
 * It never overwrites existing data: if the demo admin already exists, it does nothing.
 * Every password is BCrypt-hashed. The shared demo password comes from app.seed.password.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String ADMIN_EMAIL = "admin@societycare.local";

    private final SocietyRepository societyRepository;
    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingCampaignRepository campaignRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DataInitializer(SocietyRepository societyRepository,
                           UserRepository userRepository,
                           ServiceOfferingRepository serviceOfferingRepository,
                           BookingCampaignRepository campaignRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.seed.password}") String seedPassword) {
        this.societyRepository = societyRepository;
        this.userRepository = userRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.campaignRepository = campaignRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("Seed data already present - skipping.");
            return;
        }

        Society greenValley = society("Green Valley Residency", "12 Outer Ring Road, Marathahalli",
                "Bengaluru", "Karnataka", "560037");
        Society lakeview = society("Lakeview Heights", "45 Lake Road, HSR Layout",
                "Bengaluru", "Karnataka", "560102");

        userRepository.save(user("Admin User", ADMIN_EMAIL, "9000000001", Role.ADMIN, null));
        User provider = userRepository.save(user("Provider User", "provider@societycare.local",
                "9000000002", Role.SERVICE_PROVIDER, greenValley));
        userRepository.save(user("Citizen One", "citizen1@societycare.local", "9000000003", Role.CITIZEN, greenValley));
        userRepository.save(user("Citizen Two", "citizen2@societycare.local", "9000000004", Role.CITIZEN, greenValley));
        // Lives in another society: useful to demonstrate that cross-society joining is rejected.
        userRepository.save(user("Citizen Three", "citizen3@societycare.local", "9000000005", Role.CITIZEN, lakeview));

        ServiceOffering acService = offering("AC Servicing", "Complete split/window AC service",
                ServiceCategory.AC_SERVICING, "1000.00", provider, greenValley);
        ServiceOffering plumbing = offering("Plumbing Check-up", "Leak detection and tap/pipe repair visit",
                ServiceCategory.PLUMBING, "500.00", provider, greenValley);
        offering("Pest Control", "General pest control treatment", ServiceCategory.PEST_CONTROL,
                "1500.00", provider, greenValley);

        LocalDate today = LocalDate.now();
        // 10 residents needed: shows the PENDING state.
        campaign("AC Servicing Group Offer", acService, greenValley, 10, 25, "20.00", today, today.plusDays(30));
        // Only 2 residents needed: the two seeded Green Valley citizens can demo automatic confirmation.
        campaign("Plumbing Check-up Group Offer", plumbing, greenValley, 2, null, "15.00", today, today.plusDays(14));

        log.info("Seed data created. Demo logins (password '{}' unless SEED_PASSWORD is set): {}, "
                        + "provider@societycare.local, citizen1@societycare.local, citizen2@societycare.local, "
                        + "citizen3@societycare.local", "Password@123", ADMIN_EMAIL);
    }

    private Society society(String name, String address, String city, String state, String pincode) {
        Society s = new Society();
        s.setSocietyName(name);
        s.setAddress(address);
        s.setCity(city);
        s.setState(state);
        s.setPincode(pincode);
        return societyRepository.save(s);
    }

    private User user(String fullName, String email, String phone, Role role, Society society) {
        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(seedPassword));
        u.setPhoneNumber(phone);
        u.setRole(role);
        u.setSociety(society);
        return u;
    }

    private ServiceOffering offering(String name, String description, ServiceCategory category, String price,
                                     User provider, Society society) {
        ServiceOffering o = new ServiceOffering();
        o.setServiceName(name);
        o.setDescription(description);
        o.setCategory(category);
        o.setRegularPrice(new BigDecimal(price));
        o.setServiceProvider(provider);
        o.setSociety(society);
        o.setActive(true);
        return serviceOfferingRepository.save(o);
    }

    private void campaign(String title, ServiceOffering offering, Society society, int min, Integer max,
                          String discountPercent, LocalDate start, LocalDate end) {
        BookingCampaign c = new BookingCampaign();
        c.setCampaignTitle(title);
        c.setDescription("Join with your neighbours to get " + discountPercent + "% off.");
        c.setServiceOffering(offering);
        c.setSociety(society);
        c.setMinimumParticipants(min);
        c.setMaximumParticipants(max);
        c.setCurrentParticipants(0);
        c.setRegularPrice(offering.getRegularPrice());
        c.setDiscountPercentage(new BigDecimal(discountPercent));
        c.setDiscountedPrice(PriceCalculator.discountedPrice(offering.getRegularPrice(), new BigDecimal(discountPercent)));
        c.setStartDate(start);
        c.setEndDate(end);
        c.setStatus(CampaignStatus.PENDING);
        campaignRepository.save(c);
    }
}
