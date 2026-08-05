package fpt.qn.junglechess.user.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private static final int DEFAULT_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    public String generateSecurePassword() {
        char[] password = new char[DEFAULT_LENGTH];

        // Guarantee at least 1 character from each required pool
        password[0] = UPPER.charAt(random.nextInt(UPPER.length()));
        password[1] = LOWER.charAt(random.nextInt(LOWER.length()));
        password[2] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        password[3] = SPECIAL.charAt(random.nextInt(SPECIAL.length()));

        // Fill remaining positions randomly
        for (int i = 4; i < DEFAULT_LENGTH; i++) {
            password[i] = ALL.charAt(random.nextInt(ALL.length()));
        }

        // Fisher-Yates shuffle using SecureRandom
        for (int i = password.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        return new String(password);
    }
}
