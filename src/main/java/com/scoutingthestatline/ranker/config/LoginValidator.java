package com.scoutingthestatline.ranker.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LoginValidator {

    private static final Logger log = LoggerFactory.getLogger(LoginValidator.class);

    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "", "YOUR_FIRST_NAME", "YOUR_LAST_NAME", "YOUR_PASSWORD",
            "YourFirstName", "YourLastName", "YourPassword"
    );

    @Value("${scoresheet.login.firstname:}")
    private String firstName;

    @Value("${scoresheet.login.lastname:}")
    private String lastName;

    @Value("${scoresheet.login.password:}")
    private String password;

    @PostConstruct
    public void validateCredentials() {
        boolean hasErrors = false;
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append("\n");
        errorMessage.append("╔══════════════════════════════════════════════════════════════════╗\n");
        errorMessage.append("║              SCORESHEET LOGIN CONFIGURATION ERROR                ║\n");
        errorMessage.append("╠══════════════════════════════════════════════════════════════════╣\n");

        if (isInvalidValue(firstName)) {
            hasErrors = true;
            errorMessage.append("║  ✗ scoresheet.login.firstname is missing or has placeholder value ║\n");
        }
        if (isInvalidValue(lastName)) {
            hasErrors = true;
            errorMessage.append("║  ✗ scoresheet.login.lastname is missing or has placeholder value  ║\n");
        }
        if (isInvalidValue(password)) {
            hasErrors = true;
            errorMessage.append("║  ✗ scoresheet.login.password is missing or has placeholder value  ║\n");
        }

        if (hasErrors) {
            errorMessage.append("╠══════════════════════════════════════════════════════════════════╣\n");
            errorMessage.append("║  To fix this:                                                    ║\n");
            errorMessage.append("║  1. Copy login.properties.example to login.properties            ║\n");
            errorMessage.append("║  2. Edit login.properties with your Scoresheet credentials       ║\n");
            errorMessage.append("║                                                                  ║\n");
            errorMessage.append("║  Example:                                                        ║\n");
            errorMessage.append("║    scoresheet.login.firstname=John                               ║\n");
            errorMessage.append("║    scoresheet.login.lastname=Smith                               ║\n");
            errorMessage.append("║    scoresheet.login.password=mypassword123                       ║\n");
            errorMessage.append("╚══════════════════════════════════════════════════════════════════╝\n");

            log.error(errorMessage.toString());
            System.exit(1);
        }

        log.info("Scoresheet login credentials validated successfully");
    }

    private boolean isInvalidValue(String value) {
        return value == null || PLACEHOLDER_VALUES.contains(value.trim());
    }
}
