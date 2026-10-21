package org.star5025.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.controller.UserController;
import org.star5025.backend.dto.UserDTO;
import org.star5025.backend.dto.UserUpdateDTO;
import org.star5025.backend.entity.User;
import org.star5025.backend.exception.GlobalExceptionHandler;
import org.star5025.backend.mapper.UserMapper;
import org.star5025.backend.properties.JwtProperties;
import org.star5025.backend.result.Result;
import org.star5025.backend.service.PasswordService;
import org.star5025.backend.service.impl.UserServiceImpl;
import org.star5025.backend.vo.UserVO;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerSecurityTest {
    private static final String LEGACY = "e10adc3949ba59abbe56e057f20f883e";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private UserMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        BaseContext.removeCurrentId();
        mapper = mock(UserMapper.class);
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordService", new PasswordService());
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey("unit-test-signing-key-not-for-production");
        properties.setTtl(60000L);
        properties.setTokenName("token");
        ReflectionTestUtils.setField(controller, "jwtProperties", properties);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void cleanup() {
        BaseContext.removeCurrentId();
    }

    @Test
    void entityDtoAndVoSerializationAndToStringDoNotExposeSecrets() throws Exception {
        User user = user(1L);
        String encoded = json.writeValueAsString(Result.success(user));
        assertFalse(encoded.contains("userPassword"));
        assertFalse(encoded.contains(LEGACY));
        assertFalse(user.toString().contains(LEGACY));
        String bcryptHash = new PasswordService().encode("123456");
        user.setUserPassword(bcryptHash);
        assertFalse(json.writeValueAsString(Result.success(user)).contains(bcryptHash));
        assertFalse(user.toString().contains(bcryptHash));
        UserDTO login = json.readValue("{\"userName\":\"test\",\"userPassword\":\"secret123\"}", UserDTO.class);
        assertEquals("secret123", login.getUserPassword());
        assertFalse(login.toString().contains("secret123"));
        assertFalse(json.writeValueAsString(login).contains("secret123"));
        UserUpdateDTO change = json.readValue("{\"userId\":1,\"oldPassword\":\"old123\",\"userPassword\":\"new123\"}", UserUpdateDTO.class);
        assertEquals("old123", change.getOldPassword());
        assertEquals("new123", change.getUserPassword());
        assertFalse(change.toString().contains("old123"));
        assertFalse(change.toString().contains("new123"));
        assertFalse(json.writeValueAsString(change).contains("old123"));
        assertFalse(json.writeValueAsString(change).contains("new123"));
        assertFalse(UserVO.builder().token("private-token").build().toString().contains("private-token"));
    }

    @Test
    void profileKeepsFrontendFieldsButHasNoPasswordOrToken() throws Exception {
        BaseContext.setCurrentId(1L);
        when(mapper.getByUserId(1L)).thenReturn(user(1L));
        String response = mvc.perform(get("/api/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.userName").value("test"))
                .andExpect(jsonPath("$.data.createTime").exists())
                .andExpect(jsonPath("$.data.taskCount").value(3))
                .andExpect(jsonPath("$.data.userPassword").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(LEGACY));
    }

    @Test
    void getPutAndDeleteCannotTargetAnotherAccount() throws Exception {
        BaseContext.setCurrentId(1L);
        mvc.perform(get("/api/user/2")).andExpect(status().isForbidden());
        mvc.perform(put("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2,\"userName\":\"attacker\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/user").param("userId", "2")).andExpect(status().isForbidden());
        verifyNoInteractions(mapper);
    }

    @Test
    void actualHttpStatusesDistinguishMissingAuthenticationAndMissingOwnUser() throws Exception {
        mvc.perform(get("/api/user/1")).andExpect(status().isUnauthorized());
        BaseContext.setCurrentId(1L);
        mvc.perform(get("/api/user/1")).andExpect(status().isNotFound());
        mvc.perform(put("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"renamed\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/user")).andExpect(status().isBadRequest());
    }

    @Test
    void frontendNameAndPasswordUpdatePayloadsRemainCompatible() throws Exception {
        BaseContext.setCurrentId(1L);
        when(mapper.getByUserId(1L)).thenAnswer(invocation -> user(1L));
        when(mapper.updateName(1L, "renamed")).thenReturn(1);
        when(mapper.updateWithPassword(any(), eq(LEGACY))).thenReturn(1);
        mvc.perform(put("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"userName\":\"renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mvc.perform(put("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"oldPassword\":\"123456\",\"userPassword\":\"new123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
    }

    @Test
    void wrongOldPasswordForbiddenAndMissingOldPasswordBadRequest() throws Exception {
        BaseContext.setCurrentId(1L);
        when(mapper.getByUserId(1L)).thenReturn(user(1L));
        mvc.perform(put("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"oldPassword\":\"wrong123\",\"userPassword\":\"new123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"userPassword\":\"new123\"}"))
                .andExpect(status().isBadRequest());
        verify(mapper, never()).updateWithPassword(any(), anyString());
    }

    @Test
    void loginMigratesTest123456AndReturnsTokenWithoutHash() throws Exception {
        when(mapper.getByUserName("test")).thenReturn(user(1L));
        when(mapper.updatePasswordIfUnchanged(eq(1L), eq(LEGACY), anyString())).thenReturn(1);
        String body = mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"test\",\"userPassword\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.userPassword").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(LEGACY));
        assertFalse(body.contains("$2a$"));
    }

    @Test
    void loginFailureAndValidationUseRealStatusWithoutEchoingPassword() throws Exception {
        when(mapper.getByUserName("test")).thenReturn(user(1L));
        String failed = mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"test\",\"userPassword\":\"wrong123\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertFalse(failed.contains("wrong123"));
        mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"test\"}"))
                .andExpect(status().isBadRequest());
        String invalid = mvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"test\",\"userPassword\":\"invalid-secret!\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertFalse(invalid.contains("invalid-secret!"));
    }

    private User user(Long id) {
        return User.builder().userId(id).userName("test").userPassword(LEGACY)
                .createTime(LocalDateTime.of(2026, 10, 20, 12, 0)).taskCount(3).build();
    }
}
