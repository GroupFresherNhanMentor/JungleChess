package fpt.qn.junglechess.user.util;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import fpt.qn.junglechess.common.util.VietnameseStringUtils;
import fpt.qn.junglechess.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UsernameGenerator {

    UserRepository userRepository;
    Random random = new Random();

    public String generate(String fullName) {
        String base = buildBaseUsername(fullName);
        if (base.isEmpty()) {
            base = "user" + (1000 + random.nextInt(9000));
        }

        List<String> existingUsernames = userRepository.findUsernamesMatchingBase(base);
        if (existingUsernames.isEmpty()) {
            return base;
        }

        Pattern pattern = Pattern.compile("^" + Pattern.quote(base) + "(\\d+)?$");
        int maxSuffix = 0;
        boolean exactBaseExists = false;

        for (String username : existingUsernames) {
            Matcher matcher = pattern.matcher(username);
            if (matcher.matches()) {
                String digits = matcher.group(1);
                if (digits == null) {
                    exactBaseExists = true;
                    if (maxSuffix < 1) {
                        maxSuffix = 1;
                    }
                } else {
                    try {
                        int val = Integer.parseInt(digits);
                        if (val > maxSuffix) {
                            maxSuffix = val;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (!exactBaseExists) {
            return base;
        }

        return base + (maxSuffix + 1);
    }

    public static String buildBaseUsername(String fullName) {
        String clean = VietnameseStringUtils.removeAccents(fullName);
        if (clean.isBlank()) {
            return "";
        }

        String sanitized = clean.replaceAll("[^a-zA-Z0-9\\s]", "").trim().toLowerCase();
        if (sanitized.isBlank()) {
            return "";
        }

        String[] words = sanitized.split("\\s+");
        if (words.length == 0) {
            return "";
        }

        String lastName = words[words.length - 1];
        if (words.length == 1) {
            return lastName;
        }

        StringBuilder sb = new StringBuilder(lastName);
        for (int i = 0; i < words.length - 1; i++) {
            if (!words[i].isEmpty()) {
                sb.append(words[i].charAt(0));
            }
        }
        return sb.toString();
    }
}
