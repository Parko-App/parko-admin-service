package com.parko.admin.service.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void validate_strongPassword_isAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Clave-Segura1"));
    }

    @Test
    void validate_null_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(null));
    }

    @Test
    void validate_tooShort_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("Ab1-xyz"));
    }

    @Test
    void validate_withoutUppercase_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("clave-segura1"));
    }

    @Test
    void validate_withoutLowercase_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("CLAVE-SEGURA1"));
    }

    @Test
    void validate_withoutDigit_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("Clave-Segura-x"));
    }

    @Test
    void validate_withoutSpecialCharacter_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("ClaveSegura12"));
    }
}
