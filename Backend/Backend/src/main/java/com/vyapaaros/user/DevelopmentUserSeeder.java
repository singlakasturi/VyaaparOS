package com.vyapaaros.user;

import com.vyapaaros.security.entity.Role;
import com.vyapaaros.security.repository.RoleRepository;
import com.vyapaaros.retailer.service.DevelopmentRetailerSeedService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Locale;

@Component
public class DevelopmentUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DevelopmentRetailerSeedService retailerSeedService;
    private final boolean enabled;
    private final String email;
    private final String fullName;
    private final BigDecimal creditLimit;

    public DevelopmentUserSeeder(
        UserRepository userRepository,
        RoleRepository roleRepository,
        DevelopmentRetailerSeedService retailerSeedService,
        @Value("${app.dev-seed.enabled:true}") boolean enabled,
        @Value("${app.dev-seed.user.email}") String email,
        @Value("${app.dev-seed.user.full-name}") String fullName,
        @Value("${app.dev-seed.credit-limit:1000.00}") BigDecimal creditLimit
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.retailerSeedService = retailerSeedService;
        this.enabled = enabled;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.fullName = fullName;
        this.creditLimit = creditLimit;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            Role retailerRole = roleRepository.findByName("RETAILER")
                .orElseThrow(() -> new IllegalStateException(
                    "Required RETAILER role is missing; development user was not seeded."
                ));

            Instant now = Instant.now();
            user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setStatus("ACTIVE");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.getRoles().add(retailerRole);
            user = userRepository.save(user);
        }

        retailerSeedService.ensureRetailerMembership(user, creditLimit);
    }
}
