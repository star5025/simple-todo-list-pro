package org.star5025.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.dto.UserDTO;
import org.star5025.backend.dto.UserUpdateDTO;
import org.star5025.backend.entity.User;
import org.star5025.backend.mapper.UserMapper;
import org.star5025.backend.service.PasswordService;
import org.star5025.backend.service.impl.UserServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceSecurityTest {
    private static final String LEGACY = "e10adc3949ba59abbe56e057f20f883e";
    private UserMapper mapper;
    private UserServiceImpl service;
    private PasswordService passwords;

    @BeforeEach
    void setup() {
        BaseContext.removeCurrentId();
        mapper = mock(UserMapper.class);
        passwords = new PasswordService();
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordService", passwords);
    }

    @AfterEach
    void cleanup() {
        BaseContext.removeCurrentId();
    }

    @Test
    void registrationAlwaysStoresBcrypt() {
        service.register(credentials("test", "123456"));
        ArgumentCaptor<User> registered = ArgumentCaptor.forClass(User.class);
        verify(mapper).register(registered.capture());
        User user = registered.getValue();
        assertEquals("test", user.getUserName());
        assertTrue(user.getUserPassword().startsWith("$2a$12$"));
        assertTrue(passwords.matches("123456", user.getUserPassword()));
        assertEquals(0, user.getTaskCount());
        assertNotNull(user.getCreateTime());
    }

    @Test
    void duplicateRegistrationIsBadRequest() {
        doThrow(new DuplicateKeyException("not exposed")).when(mapper).register(any());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.register(credentials("test", "123456")));
    }

    @Test
    void bcryptLoginDoesNotRewritePassword() {
        User user = user(1L, passwords.encode("123456"));
        when(mapper.getByUserName("test")).thenReturn(user);
        assertSame(user, service.login(credentials("test", "123456")));
        verify(mapper, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
    }

    @Test
    void bcryptWrongPasswordAndUnknownUserAreUnauthorized() {
        when(mapper.getByUserName("test")).thenReturn(user(1L, passwords.encode("123456")));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.login(credentials("test", "wrong123")));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.login(credentials("missing", "123456")));
        verify(mapper, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
    }

    @Test
    void successfulLegacyLoginMigratesUsingCompareAndSet() {
        when(mapper.getByUserName("test")).thenReturn(user(1L, LEGACY));
        when(mapper.updatePasswordIfUnchanged(eq(1L), eq(LEGACY), anyString())).thenReturn(1);
        User loggedIn = service.login(credentials("test", "123456"));
        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(mapper).updatePasswordIfUnchanged(eq(1L), eq(LEGACY), newHash.capture());
        assertTrue(newHash.getValue().startsWith("$2a$12$"));
        assertTrue(passwords.matches("123456", newHash.getValue()));
        assertEquals(newHash.getValue(), loggedIn.getUserPassword());
    }

    @Test
    void loginMigratesHistoricalPasswordsOutsideNewPasswordPolicy() throws Exception {
        for (String raw : new String[]{"abc", "a".repeat(73)}) {
            String legacy = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            when(mapper.getByUserName("test")).thenReturn(user(1L, legacy));
            when(mapper.updatePasswordIfUnchanged(eq(1L), eq(legacy), anyString())).thenReturn(1);
            User loggedIn = service.login(credentials("test", raw));
            assertTrue(passwords.matches(raw, loggedIn.getUserPassword()));
            assertFalse(passwords.isLegacyMd5(loggedIn.getUserPassword()));
        }
    }

    @Test
    void failedLegacyLoginNeverMigrates() {
        when(mapper.getByUserName("test")).thenReturn(user(1L, LEGACY));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.login(credentials("test", "wrong123")));
        verify(mapper, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
    }

    @Test
    void migrationCannotOverwriteConcurrentPasswordChange() {
        when(mapper.getByUserName("test")).thenReturn(user(1L, LEGACY));
        when(mapper.getByUserId(1L)).thenReturn(user(1L, passwords.encode("changed123")));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.login(credentials("test", "123456")));
        verify(mapper, times(1)).updatePasswordIfUnchanged(eq(1L), eq(LEGACY), anyString());
    }

    @Test
    void concurrentMigrationByAnotherLoginIsReverified() {
        when(mapper.getByUserName("test")).thenReturn(user(1L, LEGACY));
        User migrated = user(1L, passwords.encode("123456"));
        when(mapper.getByUserId(1L)).thenReturn(migrated);
        assertSame(migrated, service.login(credentials("test", "123456")));
    }

    @Test
    void migrationWithDeletedOrUnchangedLegacyRecordFailsClosed() {
        when(mapper.getByUserName("test")).thenReturn(user(1L, LEGACY));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.login(credentials("test", "123456")));
        when(mapper.getByUserId(1L)).thenReturn(user(1L, LEGACY));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.login(credentials("test", "123456")));
    }

    @Test
    void migrationDatabaseFailureDoesNotPermitWeakFallback() {
        when(mapper.getByUserName("test")).thenReturn(user(1L, LEGACY));
        when(mapper.updatePasswordIfUnchanged(eq(1L), eq(LEGACY), anyString()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> service.login(credentials("test", "123456")));
    }

    @Test
    void twoAccountsCannotReadModifyDeleteOrVerifyEachOther() {
        User alice = user(1L, LEGACY);
        User bob = user(2L, LEGACY);
        when(mapper.getByUserId(1L)).thenReturn(alice);
        when(mapper.getByUserId(2L)).thenReturn(bob);
        BaseContext.setCurrentId(1L);
        assertSame(alice, service.getUserById(1L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getUserById(2L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updateUser(update(2L, "renamed", null, null)));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deleteUser(bob));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.verifyPassword(2L, "123456"));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.incrementTaskCount(2L, 1));
        BaseContext.setCurrentId(2L);
        assertSame(bob, service.getUserById(2L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getUserById(1L));
        verify(mapper, never()).updateName(anyLong(), anyString());
        verify(mapper, never()).updateWithPassword(any(), anyString());
        verify(mapper, never()).delete(any());
    }

    @Test
    void absentContextIsUnauthorizedAndMissingOwnRecordIsNotFound() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.getUserById(1L));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.updateUser(update(1L, "renamed", null, null)));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.deleteUser(user(1L, LEGACY)));
        BaseContext.setCurrentId(1L);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getUserById(1L));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.getUserById(null));
    }

    @Test
    void passwordChangeRequiresBothPasswordsAndRejectsWrongOldPassword() {
        BaseContext.setCurrentId(1L);
        when(mapper.getByUserId(1L)).thenReturn(user(1L, LEGACY));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateUser(update(1L, null, null, "new123")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateUser(update(1L, null, "123456", null)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateUser(update(1L, null, "", "new123")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateUser(update(1L, null, null, null)));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updateUser(update(1L, null, "wrong123", "new123")));
        verify(mapper, never()).updateWithPassword(any(), anyString());
    }

    @Test
    void passwordChangeFromLegacyOrBcryptAlwaysStoresBcrypt() {
        BaseContext.setCurrentId(1L);
        for (String oldHash : new String[]{LEGACY, passwords.encode("123456")}) {
            when(mapper.getByUserId(1L)).thenReturn(user(1L, oldHash));
            when(mapper.updateWithPassword(any(), eq(oldHash))).thenReturn(1);
            service.updateUser(update(1L, null, "123456", "new123"));
            ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
            verify(mapper).updateWithPassword(updated.capture(), eq(oldHash));
            assertTrue(updated.getValue().getUserPassword().startsWith("$2a$12$"));
            assertTrue(passwords.matches("new123", updated.getValue().getUserPassword()));
            assertFalse(passwords.matches("123456", updated.getValue().getUserPassword()));
        }
    }

    @Test
    void nameOnlyUpdateNeverRewritesOrDowngradesStoredHash() {
        BaseContext.setCurrentId(1L);
        when(mapper.getByUserId(1L)).thenReturn(user(1L, LEGACY));
        when(mapper.updateName(1L, "renamed")).thenReturn(1);
        service.updateUser(update(1L, "renamed", null, null));
        verify(mapper).updateName(1L, "renamed");
        verify(mapper, never()).updateWithPassword(any(), anyString());
        verify(mapper, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
    }

    @Test
    void concurrentPasswordUpdateFailsAndDeletedUserIsNotFound() {
        BaseContext.setCurrentId(1L);
        when(mapper.getByUserId(1L)).thenReturn(user(1L, LEGACY));
        when(mapper.existsById(1L)).thenReturn(true);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updateUser(update(1L, null, "123456", "new123")));
        when(mapper.getByUserId(1L)).thenReturn(user(1L, LEGACY));
        when(mapper.existsById(1L)).thenReturn(false);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateUser(update(1L, null, "123456", "new123")));
    }

    @Test
    void deleteOnlySelfAndReportMissingRecord() {
        BaseContext.setCurrentId(1L);
        User own = user(1L, LEGACY);
        when(mapper.delete(own)).thenReturn(1, 0);
        service.deleteUser(own);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteUser(own));
    }

    @Test
    void verifyOwnPasswordSupportsLegacyAndBcrypt() {
        BaseContext.setCurrentId(1L);
        when(mapper.getByUserId(1L)).thenReturn(user(1L, LEGACY));
        assertTrue(service.verifyPassword(1L, "123456"));
        assertFalse(service.verifyPassword(1L, "wrong123"));
        when(mapper.getByUserId(1L)).thenReturn(user(1L, passwords.encode("123456")));
        assertTrue(service.verifyPassword(1L, "123456"));
    }

    private UserDTO credentials(String name, String password) {
        return UserDTO.builder().userName(name).userPassword(password).build();
    }

    private User user(Long id, String hash) {
        return User.builder().userId(id).userName("test").userPassword(hash).taskCount(0).build();
    }

    private UserUpdateDTO update(Long id, String name, String oldPassword, String newPassword) {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setUserId(id);
        dto.setUserName(name);
        dto.setOldPassword(oldPassword);
        dto.setUserPassword(newPassword);
        return dto;
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatus());
    }
}
