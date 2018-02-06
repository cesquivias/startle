package startle;

import javax.annotation.Nonnull;

import static java.lang.Character.toUpperCase;

public class StringUtils {
    public static String capitalize(@Nonnull String str) {
        if (str.isEmpty()) {
            return str;
        }
        return toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
