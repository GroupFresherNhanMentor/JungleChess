package fpt.qn.iothospital.common.util;

import java.text.Normalizer;

public final class VietnameseStringUtils {

    private VietnameseStringUtils() {
    }

    public static String removeAccents(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String str = input.replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(str, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }
}
