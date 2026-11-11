package com.societycare.integration;

import com.societycare.entity.BookingCampaign;
import com.societycare.entity.ServiceOffering;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.BookingStatus;
import com.societycare.enums.CampaignStatus;
import com.societycare.enums.Role;
import com.societycare.enums.ServiceCategory;
import com.societycare.exception.BusinessRuleException;
import com.societycare.exception.DuplicateResourceException;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.BookingRepository;
import com.societycare.repository.ServiceOfferingRepository;
import com.societycare.repository.SocietyRepository;
import com.societycare.repository.UserRepository;
import com.societycare.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Spring context + real database transactions (H2 in-memory, see application-test.yml).
 * Many threads join the same campaign at the same moment; the pessimistic lock must keep everything consistent.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {

    private static final int CITIZEN_COUNT = 20;

    @Autowired
    private BookingService bookingService;
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

    private Long campaignId;
    private final List<String> citizenEmails = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        campaignRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        userRepository.deleteAll();
        societyRepository.deleteAll();
        citizenEmails.clear();

        Society society = new Society();
        society.setSocietyName("Concurrency Heights");
        society.setAddress("1 Test Road");
        society.setCity("Bengaluru");
        society.setState("Karnataka");
        society.setPincode("560001");
        society = societyRepository.save(society);

        User provider = saveUser("provider@test.local", Role.SERVICE_PROVIDER, society);
        for (int i = 1; i <= CITIZEN_COUNT; i++) {
            String email = "citizen" + i + "@test.local";
            saveUser(email, Role.CITIZEN, society);
            citizenEmails.add(email);
        }

        ServiceOffering offering = new ServiceOffering();
        offering.setServiceName("AC Servicing");
        offering.setCategory(ServiceCategory.AC_SERVICING);
        offering.setRegularPrice(new BigDecimal("1000.00"));
        offering.setServiceProvider(provider);
        offering.setSociety(society);
        offering.setActive(true);
        offering = serviceOfferingRepository.save(offering);

        BookingCampaign campaign = new BookingCampaign();
        campaign.setCampaignTitle("Concurrent campaign");
        campaign.setServiceOffering(offering);
        campaign.setSociety(society);
        campaign.setMinimumParticipants(10);
        campaign.setMaximumParticipants(15);
        campaign.setCurrentParticipants(0);
        campaign.setRegularPrice(new BigDecimal("1000.00"));
        campaign.setDiscountPercentage(new BigDecimal("20.00"));
        campaign.setDiscountedPrice(new BigDecimal("800.00"));
        campaign.setStartDate(LocalDate.now());
        campaign.setEndDate(LocalDate.now().plusDays(10));
        campaign.setStatus(CampaignStatus.PENDING);
        campaignId = campaignRepository.save(campaign).getId();
    }

    private User saveUser(String email, Role role, Society society) {
        User user = new User();
        user.setFullName(email);
        user.setEmail(email);
        user.setPassword("not-used-in-this-test");
        user.setPhoneNumber("9000000000");
        user.setRole(role);
        user.setSociety(society);
        return userRepository.save(user);
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void twentyResidentsJoiningAtOnceNeverExceedTheMaximumAndConfirmExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CITIZEN_COUNT);
        CountDownLatch ready = new CountDownLatch(CITIZEN_COUNT);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<String> unexpected = java.util.Collections.synchronizedList(new ArrayList<>());

        List<Future<Void>> futures = new ArrayList<>();
        for (String email : citizenEmails) {
            Callable<Void> task = () -> {
                ready.countDown();
                go.await();
                authenticateAs(email);
                try {
                    bookingService.joinCampaign(campaignId);
                    succeeded.incrementAndGet();
                } catch (BusinessRuleException ex) {
                    rejected.incrementAndGet();
                } catch (Exception ex) {
                    unexpected.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        for (Future<Void> f : futures) {
            f.get(90, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unexpected).as("unexpected exceptions").isEmpty();
        assertThat(succeeded.get()).as("successful joins").isEqualTo(15);
        assertThat(rejected.get()).as("rejected joins").isEqualTo(5);

        BookingCampaign campaign = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaign.getCurrentParticipants()).isEqualTo(15);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CONFIRMED);
        assertThat(bookingRepository.countByBookingCampaignIdAndBookingStatus(campaignId, BookingStatus.BOOKED))
                .isEqualTo(15L);
    }

    @Test
    void sameResidentJoiningManyTimesAtOnceCreatesExactlyOneBooking() throws Exception {
        int attempts = 8;
        String email = citizenEmails.get(0);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<String> unexpected = java.util.Collections.synchronizedList(new ArrayList<>());

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Callable<Void> task = () -> {
                ready.countDown();
                go.await();
                authenticateAs(email);
                try {
                    bookingService.joinCampaign(campaignId);
                    succeeded.incrementAndGet();
                } catch (DuplicateResourceException ex) {
                    duplicates.incrementAndGet();
                } catch (Exception ex) {
                    unexpected.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        for (Future<Void> f : futures) {
            f.get(90, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unexpected).as("unexpected exceptions").isEmpty();
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(attempts - 1);
        assertThat(campaignRepository.findById(campaignId).orElseThrow().getCurrentParticipants()).isEqualTo(1);
        assertThat(bookingRepository.countByBookingCampaignIdAndBookingStatus(campaignId, BookingStatus.BOOKED))
                .isEqualTo(1L);
    }
}
