package com.parko.admin.service.service;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.exception.ExternalServiceException;
import org.springframework.stereotype.Component;

@Component
public class FirebaseUserGateway {

    private final FirebaseAuth firebaseAuth;

    public FirebaseUserGateway(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    public String createUser(String email, String password, String displayName, boolean emailVerified) {
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(password)
                .setDisplayName(displayName)
                .setEmailVerified(emailVerified);
        try {
            return firebaseAuth.createUser(request).getUid();
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new ConflictException("Ya existe una cuenta en Firebase con el email: " + email);
            }
            throw new ExternalServiceException("No se pudo crear el usuario en Firebase: " + e.getMessage(), e);
        }
    }

    public void deleteUser(String uid) {
        try {
            firebaseAuth.deleteUser(uid);
        } catch (FirebaseAuthException e) {
            throw new ExternalServiceException("No se pudo eliminar el usuario en Firebase: " + e.getMessage(), e);
        }
    }

    public void updateUser(String uid, String email, String displayName, Boolean disabled, Boolean emailVerified) {
        UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(uid);
        if (email != null) {
            request.setEmail(email);
        }
        if (displayName != null) {
            request.setDisplayName(displayName);
        }
        if (disabled != null) {
            request.setDisabled(disabled);
        }
        if (emailVerified != null) {
            request.setEmailVerified(emailVerified);
        }
        try {
            firebaseAuth.updateUser(request);
        } catch (FirebaseAuthException e) {
            throw new ExternalServiceException("No se pudo actualizar el usuario en Firebase: " + e.getMessage(), e);
        }
    }
}
