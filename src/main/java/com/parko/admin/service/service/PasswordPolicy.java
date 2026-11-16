package com.parko.admin.service.service;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    private static final Pattern SPECIAL_CHARACTER = Pattern.compile("[^A-Za-z0-9]");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("La contraseña debe contener al menos una letra mayúscula");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            throw new IllegalArgumentException("La contraseña debe contener al menos una letra minúscula");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("La contraseña debe contener al menos un número");
        }
        if (!SPECIAL_CHARACTER.matcher(password).find()) {
            throw new IllegalArgumentException("La contraseña debe contener al menos un carácter especial");
        }
    }
}
