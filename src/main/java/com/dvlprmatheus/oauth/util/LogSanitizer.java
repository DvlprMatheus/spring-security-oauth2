package com.dvlprmatheus.oauth.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogSanitizer {

  private static final String MASK = "***";
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^([^@\\s]+)@([^@\\s]+)$");
  private static final Pattern EMBEDDED_EMAIL_PATTERN =
      Pattern.compile("[^@\\s'\"<>(),;:]+@[^@\\s'\"<>(),;:]+");
  private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("[\\r\\n]+");

  private LogSanitizer() {}

  public static String sanitizeText(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    String singleLine = LINE_BREAK_PATTERN.matcher(value).replaceAll(" ");
    return EMBEDDED_EMAIL_PATTERN
        .matcher(singleLine)
        .replaceAll(match -> Matcher.quoteReplacement(sanitize(match.group())));
  }

  public static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    Matcher matcher = EMAIL_PATTERN.matcher(value.trim());
    if (matcher.matches()) {
      return maskEmail(matcher.group(1), matcher.group(2));
    }
    return mask(value.trim());
  }

  private static String maskEmail(String localPart, String domain) {
    int lastDot = domain.lastIndexOf('.');
    if (lastDot <= 0) {
      return mask(localPart) + "@" + mask(domain);
    }
    return mask(localPart) + "@" + mask(domain.substring(0, lastDot)) + domain.substring(lastDot);
  }

  private static String mask(String value) {
    if (value.length() <= 2) {
      return MASK;
    }
    if (value.length() <= 4) {
      return value.charAt(0) + MASK;
    }
    return value.charAt(0) + MASK + value.charAt(value.length() - 1);
  }
}
