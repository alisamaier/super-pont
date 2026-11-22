package ro.superpont.auth.validation;

import java.util.regex.Pattern;

public final class DisplayName {
    private static final Pattern EDGES = Pattern.compile("^[\\p{javaWhitespace}\\p{Z}\\u0085\\uFEFF]+|[\\p{javaWhitespace}\\p{Z}\\u0085\\uFEFF]+$");

    private DisplayName() {}

    public static String normalize(String name) {
        return name == null ? null : EDGES.matcher(name).replaceAll("");
    }
}
