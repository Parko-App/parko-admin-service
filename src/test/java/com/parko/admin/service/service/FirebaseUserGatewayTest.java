package com.parko.admin.service.service;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebaseUserGatewayTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(UserRecord.UpdateRequest request) throws Exception {
        Method method = UserRecord.UpdateRequest.class.getDeclaredMethod("getProperties", JsonFactory.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(request, GsonFactory.getDefaultInstance());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(UserRecord.CreateRequest request) throws Exception {
        Method method = UserRecord.CreateRequest.class.getDeclaredMethod("getProperties");
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(request);
    }

    @Test
    void createUser_sendsAllFieldsAndReturnsUid() throws Exception {
        UserRecord record = mock(UserRecord.class);
        when(record.getUid()).thenReturn("uid-new");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(record);

        String uid = new FirebaseUserGateway(firebaseAuth).createUser("a@b.c", "Clave-Segura1", "Ana", true);

        assertEquals("uid-new", uid);
        ArgumentCaptor<UserRecord.CreateRequest> request = ArgumentCaptor.forClass(UserRecord.CreateRequest.class);
        verify(firebaseAuth).createUser(request.capture());
        assertEquals("a@b.c", properties(request.getValue()).get("email"));
        assertEquals("Clave-Segura1", properties(request.getValue()).get("password"));
        assertEquals("Ana", properties(request.getValue()).get("displayName"));
        assertEquals(true, properties(request.getValue()).get("emailVerified"));
    }

    @Test
    void createUser_emailAlreadyExists_throwsConflict() throws FirebaseAuthException {
        FirebaseAuthException error = mock(FirebaseAuthException.class);
        when(error.getAuthErrorCode()).thenReturn(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(error);

        FirebaseUserGateway gateway = new FirebaseUserGateway(firebaseAuth);

        assertThrows(ConflictException.class, () -> gateway.createUser("a@b.c", "Clave-Segura1", "Ana", false));
    }

    @Test
    void createUser_otherFirebaseError_throwsExternalServiceException() throws FirebaseAuthException {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(mock(FirebaseAuthException.class));

        FirebaseUserGateway gateway = new FirebaseUserGateway(firebaseAuth);

        assertThrows(ExternalServiceException.class, () -> gateway.createUser("a@b.c", "Clave-Segura1", "Ana", false));
    }

    @Test
    void deleteUser_deletesByUid() throws FirebaseAuthException {
        new FirebaseUserGateway(firebaseAuth).deleteUser("uid-1");

        verify(firebaseAuth).deleteUser("uid-1");
    }

    @Test
    void deleteUser_firebaseError_throwsExternalServiceException() throws FirebaseAuthException {
        doThrow(mock(FirebaseAuthException.class)).when(firebaseAuth).deleteUser("uid-1");

        FirebaseUserGateway gateway = new FirebaseUserGateway(firebaseAuth);

        assertThrows(ExternalServiceException.class, () -> gateway.deleteUser("uid-1"));
    }

    @Test
    void updateUser_sendsOnlyGivenFields() throws Exception {
        new FirebaseUserGateway(firebaseAuth).updateUser("uid-1", "nuevo@b.c", "Ana", true, false);

        ArgumentCaptor<UserRecord.UpdateRequest> request = ArgumentCaptor.forClass(UserRecord.UpdateRequest.class);
        verify(firebaseAuth).updateUser(request.capture());
        assertEquals("uid-1", properties(request.getValue()).get("localId"));
        assertEquals("nuevo@b.c", properties(request.getValue()).get("email"));
        assertEquals("Ana", properties(request.getValue()).get("displayName"));
        assertEquals(true, properties(request.getValue()).get("disableUser"));
        assertEquals(false, properties(request.getValue()).get("emailVerified"));
    }

    @Test
    void updateUser_withNullFields_onlyIdentifiesUser() throws Exception {
        new FirebaseUserGateway(firebaseAuth).updateUser("uid-1", null, null, null, null);

        ArgumentCaptor<UserRecord.UpdateRequest> request = ArgumentCaptor.forClass(UserRecord.UpdateRequest.class);
        verify(firebaseAuth).updateUser(request.capture());
        assertEquals(1, properties(request.getValue()).size());
    }

    @Test
    void updateUser_firebaseError_throwsExternalServiceException() throws FirebaseAuthException {
        when(firebaseAuth.updateUser(any(UserRecord.UpdateRequest.class))).thenThrow(mock(FirebaseAuthException.class));

        FirebaseUserGateway gateway = new FirebaseUserGateway(firebaseAuth);

        assertThrows(ExternalServiceException.class, () -> gateway.updateUser("uid-1", null, "Ana", null, null));
    }
}
