package com.parko.admin.service.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.parko.admin.service.exception.ExternalServiceException;
import org.springframework.stereotype.Component;

@Component
public class FirebaseUserGateway {

    private final FirebaseAuth firebaseAuth;

    public FirebaseUserGateway(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
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
