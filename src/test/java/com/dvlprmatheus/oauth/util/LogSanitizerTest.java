package com.dvlprmatheus.oauth.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

  @Test
  void shouldMaskEmailKeepingOnlyEdgesAndTopLevelDomain() {
    assertThat(LogSanitizer.sanitize("joao.silva@gmail.com")).isEqualTo("j***a@g***l.com");
  }

  @Test
  void shouldMaskShortEmailParts() {
    assertThat(LogSanitizer.sanitize("ab@xy.com.br")).isEqualTo("***@x***m.br");
  }

  @Test
  void shouldMaskEmailWithoutTopLevelDomain() {
    assertThat(LogSanitizer.sanitize("joao@localhost")).isEqualTo("j***@l***t");
  }

  @Test
  void shouldMaskGenericValue() {
    assertThat(LogSanitizer.sanitize("matheus")).isEqualTo("m***s");
    assertThat(LogSanitizer.sanitize("1f2e3d4c-aaaa-bbbb-cccc-123456789abc")).isEqualTo("1***c");
  }

  @Test
  void shouldFullyMaskVeryShortValues() {
    assertThat(LogSanitizer.sanitize("ab")).isEqualTo("***");
    assertThat(LogSanitizer.sanitize("abcd")).isEqualTo("a***");
  }

  @Test
  void shouldKeepNullAndBlankValues() {
    assertThat(LogSanitizer.sanitize(null)).isNull();
    assertThat(LogSanitizer.sanitize("  ")).isEqualTo("  ");
  }

  @Test
  void shouldMaskEmailsEmbeddedInFreeText() {
    assertThat(
            LogSanitizer.sanitizeText(
                "AADSTS50020: User account 'joao.silva@gmail.com' does not exist"))
        .isEqualTo("AADSTS50020: User account 'j***a@g***l.com' does not exist");
  }

  @Test
  void shouldKeepFreeTextWithoutEmailsReadable() {
    assertThat(LogSanitizer.sanitizeText("invalid_scope")).isEqualTo("invalid_scope");
  }

  @Test
  void shouldRemoveLineBreaksToPreventLogInjection() {
    assertThat(LogSanitizer.sanitizeText("invalid_request\r\nINFO fake log line"))
        .isEqualTo("invalid_request INFO fake log line");
  }

  @Test
  void shouldKeepNullAndBlankFreeText() {
    assertThat(LogSanitizer.sanitizeText(null)).isNull();
    assertThat(LogSanitizer.sanitizeText(" ")).isEqualTo(" ");
  }
}
