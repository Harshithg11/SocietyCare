package com.societycare.integration;

import com.societycare.entity.Booking;
import com.societycare.entity.BookingCampaign;
import com.societycare.entity.ServiceOffering;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.BookingStatus;
import com.societycare.enums.CampaignStatus;
import com.societycare.enums.Role;
import com.societycare.enums.ServiceCategory;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.BookingRepository;
import com.societycare.repository.ServiceOfferingRepository;
import com.societycare.repository.SocietyRepository;
import com.societycare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack tests: real filter chain, real JWT, real services, H2 database.
 * MockMvc is built manually so the test does not depend on Spring Boot test auto-configuration package names.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityAuthorizationTest {

    private static final String PASSWORD = "Password@123";
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingCampaignRepository campaignRepository;
    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocietyRepository societyRepository;

    private MockMvc mockMvc;
    private Society society;
    private BookingCampaign campaign;
    private Booking bookingOfCitizenA;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        bookingRepository.deleteAll();
        campaignRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        userRepository.deleteAll();
        societyRepository.deleteAll();

        society = new Society();
        society.setSocietyName("Security Heights");
        society.setAddress("2 Test Road");
        society.setCity("Bengaluru");
        society.setState("Karnataka");
        society.setPincode("560002");
        society = societyRepository.save(society);

        saveUser("admin@test.local", Role.ADMIN, null);
        User provider = saveUser("provider@test.local", Role.SERVICE_PROVIDER, society);
        User citizenA = saveUser("citizena@test.local", Role.CITIZEN, society);
        saveUser("citizenb@test.local", Role.CITIZEN, society);

        ServiceOffering offering = new ServiceOffering();
        offering.setServiceName("Plumbing Check-up");
        offering.setCategory(ServiceCategory.PLUMBING);
        offering.setRegularPrice(new BigDecimal("500.00"));
        offering.setServiceProvider(provider);
        offering.setSociety(society);
        offering.setActive(true);
        offering = serviceOfferingRepository.save(offering);

        campaign = new BookingCampaign();
        campaign.setCampaignTitle("Plumbing Offer");
        campaign.setServiceOffering(offering);
        campaign.setSociety(society);
        campaign.setMinimumParticipants(10);
        campaign.setCurrentParticipants(1);
        campaign.setRegularPrice(new BigDecimal("500.00"));
        campaign.setDiscountPercentage(new BigDecimal("15.00"));
        campaign.setDiscountedPrice(new BigDecimal("425.00"));
        campaign.setStartDate(LocalDate.now());
        campaign.setEndDate(LocalDate.now().plusDays(10));
        campaign.setStatus(CampaignStatus.PENDING);
        campaign = campaignRepository.save(campaign);

        bookingOfCitizenA = new Booking();
        bookingOfCitizenA.setCitizen(citizenA);
        bookingOfCitizenA.setBookingCampaign(campaign);
        bookingOfCitizenA.setBookingStatus(BookingStatus.BOOKED);
        bookingOfCitizenA.setBookedAt(LocalDateTime.now());
        bookingOfCitizenA = bookingRepository.save(bookingOfCitizenA);
    }

    private User saveUser(String email, Role role, Society s) {
        User user = new User();
        user.setFullName(email);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setPhoneNumber("9000000000");
        user.setRole(role);
        user.setSociety(s);
        return userRepository.save(user);
    }

    private String loginAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(body);
        assertThat(matcher.find()).as("token present in login response").isTrue();
        return matcher.group(1);
    }

    private String campaignJson() {
        LocalDate today = LocalDate.now();
        return "{\"campaignTitle\":\"New Offer\",\"serviceOfferingId\":1,\"societyId\":" + society.getId()
                + ",\"minimumParticipants\":5,\"regularPrice\":500.00,\"discountPercentage\":10.00,"
                + "\"startDate\":\"" + today + "\",\"endDate\":\"" + today.plusDays(7) + "\"}";
    }

    private String serviceJson() {
        return "{\"serviceName\":\"Cleaning\",\"category\":\"CLEANING\",\"regularPrice\":800.00}";
    }

    private String societyJson() {
        return "{\"societyName\":\"New Society\",\"address\":\"9 Main Road\",\"city\":\"Pune\","
                + "\"state\":\"Maharashtra\",\"pincode\":\"411001\"}";
    }

    // ---------------- authentication ----------------

    @Test
    void unauthenticatedUserCannotAccessProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/campaigns")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/my-bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings/campaigns/" + campaign.getId() + "/join"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejectedWithCleanJson401() throws Exception {
        mockMvc.perform(get("/api/campaigns").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("\"status\":401")));
    }

    @Test
    void publicSocietyListNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/societies"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Security Heights")));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"citizena@test.local\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationCreatesCitizenAndNeverReturnsPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Resident\",\"email\":\"new@test.local\","
                                + "\"password\":\"Resident@123\",\"phoneNumber\":\"9876543210\","
                                + "\"societyId\":" + society.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"role\":\"CITIZEN\"")))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void publicRegistrationAsAdminIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Sneaky\",\"email\":\"sneaky@test.local\","
                                + "\"password\":\"Sneaky@123\",\"phoneNumber\":\"9876543210\","
                                + "\"societyId\":" + society.getId() + ",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        assertThat(userRepository.existsByEmail("sneaky@test.local")).isFalse();
    }

    @Test
    void duplicateEmailRegistrationReturns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Again\",\"email\":\"citizena@test.local\","
                                + "\"password\":\"Resident@123\",\"phoneNumber\":\"9876543210\","
                                + "\"societyId\":" + society.getId() + "}"))
                .andExpect(status().isConflict());
    }

    // ---------------- role-based authorization ----------------

    @Test
    void citizenCannotCreateCampaign() throws Exception {
        String token = loginAndGetToken("citizena@test.local");
        mockMvc.perform(post("/api/campaigns").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(campaignJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenCannotCreateServiceOffering() throws Exception {
        String token = loginAndGetToken("citizena@test.local");
        mockMvc.perform(post("/api/services").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(serviceJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCannotCreateSociety() throws Exception {
        String token = loginAndGetToken("provider@test.local");
        mockMvc.perform(post("/api/societies").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(societyJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateSociety() throws Exception {
        String token = loginAndGetToken("admin@test.local");
        mockMvc.perform(post("/api/societies").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(societyJson()))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("New Society")));
    }

    @Test
    void citizenCannotReadAnotherCitizensBooking() throws Exception {
        String tokenB = loginAndGetToken("citizenb@test.local");
        mockMvc.perform(get("/api/bookings/" + bookingOfCitizenA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenCanReadOwnBooking() throws Exception {
        String tokenA = loginAndGetToken("citizena@test.local");
        mockMvc.perform(get("/api/bookings/" + bookingOfCitizenA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"bookingStatus\":\"BOOKED\"")));
    }

    @Test
    void citizenCanJoinCampaignOnceThenGetsConflict() throws Exception {
        String tokenB = loginAndGetToken("citizenb@test.local");
        mockMvc.perform(post("/api/bookings/campaigns/" + campaign.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"currentParticipants\":2")))
                .andExpect(content().string(containsString("\"campaignStatus\":\"PENDING\"")));

        mockMvc.perform(post("/api/bookings/campaigns/" + campaign.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isConflict());
    }

    @Test
    void providerCannotJoinCampaigns() throws Exception {
        String token = loginAndGetToken("provider@test.local");
        mockMvc.perform(post("/api/bookings/campaigns/" + campaign.getId() + "/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
