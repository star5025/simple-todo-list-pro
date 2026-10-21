package org.star5025.backend;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.controller.UserController;
import org.star5025.backend.interceptor.JwtTokenInterceptor;
import org.star5025.backend.mapper.UserMapper;
import org.star5025.backend.properties.JwtProperties;
import org.star5025.backend.utils.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtTokenInterceptorTest {
    private static final String SIGNING_KEY = "unit-test-signing-key-not-for-production";
    private JwtTokenInterceptor interceptor;
    private UserMapper mapper;
    private HandlerMethod handler;

    @BeforeEach
    void setup() throws Exception {
        BaseContext.removeCurrentId();
        interceptor = new JwtTokenInterceptor();
        mapper = mock(UserMapper.class);
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey(SIGNING_KEY);
        properties.setTokenName("token");
        ReflectionTestUtils.setField(interceptor, "jwtProperties", properties);
        ReflectionTestUtils.setField(interceptor, "userMapper", mapper);
        handler = new HandlerMethod(new UserController(), UserController.class.getMethod("logout"));
    }

    @AfterEach
    void cleanup() {
        BaseContext.removeCurrentId();
    }

    @Test
    void validTokenSetsIdentityAndCompletionAlwaysClearsIt() {
        when(mapper.existsById(1L)).thenReturn(true);
        MockHttpServletRequest request = request(token(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, handler));
        assertEquals(1L, BaseContext.getCurrentId());
        interceptor.afterCompletion(request, response, handler, null);
        assertNull(BaseContext.getCurrentId());
        assertTrue(interceptor.preHandle(request, response, handler));
        interceptor.afterCompletion(request, response, handler, new RuntimeException("controller failed"));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void missingAndMalformedTokensRejectAndClearPreviousIdentity() {
        for (String token : new String[]{null, "", "bad-token"}) {
            BaseContext.setCurrentId(99L);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request(token), response, handler));
            assertEquals(401, response.getStatus());
            assertNull(BaseContext.getCurrentId());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void expiredTokenAndMissingOrMalformedIdentityAreUnauthorized() {
        Map<String, Object> claims = new HashMap<>();
        String missingId = JwtUtil.createJWT(SIGNING_KEY, 60000L, claims);
        claims.put("userId", 1L);
        String expired = JwtUtil.createJWT(SIGNING_KEY, -10000L, claims);
        claims.put("userId", "not-an-id");
        String malformed = JwtUtil.createJWT(SIGNING_KEY, 60000L, claims);
        String noExpiration = Jwts.builder().claim("userId", 1L)
                .signWith(SignatureAlgorithm.HS256, SIGNING_KEY.getBytes(StandardCharsets.UTF_8)).compact();
        for (String token : new String[]{missingId, expired, malformed, noExpiration, token(0L), token(-1L)}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request(token), response, handler));
            assertEquals(401, response.getStatus());
            assertNull(BaseContext.getCurrentId());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void deletedAccountCannotReusePreviouslyValidToken() {
        when(mapper.existsById(1L)).thenReturn(true, false);
        MockHttpServletRequest request = request(token(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, handler));
        assertEquals(1L, BaseContext.getCurrentId());
        interceptor.afterCompletion(request, response, handler, null);
        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(401, response.getStatus());
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void consecutiveRequestsFromTwoAccountsNeverShareIdentity() {
        when(mapper.existsById(1L)).thenReturn(true);
        when(mapper.existsById(2L)).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest first = request(token(1L));
        MockHttpServletRequest second = request(token(2L));
        assertTrue(interceptor.preHandle(first, response, handler));
        assertEquals(1L, BaseContext.requireCurrentId());
        interceptor.afterCompletion(first, response, handler, null);
        assertNull(BaseContext.getCurrentId());
        assertTrue(interceptor.preHandle(second, response, handler));
        assertEquals(2L, BaseContext.requireCurrentId());
        interceptor.afterCompletion(second, response, handler, null);
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void optionsAndNonHandlerRequestsClearContextWithoutAuthentication() {
        MockHttpServletRequest request = request(null);
        request.setMethod("OPTIONS");
        BaseContext.setCurrentId(99L);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
        assertNull(BaseContext.getCurrentId());
        BaseContext.setCurrentId(99L);
        request.setMethod("GET");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(BaseContext.getCurrentId());
        verifyNoInteractions(mapper);
    }

    @Test
    void asynchronousHandoffClearsOriginalThreadContext() {
        BaseContext.setCurrentId(1L);
        interceptor.afterConcurrentHandlingStarted(request(null), new MockHttpServletResponse(), handler);
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void databaseFailureDoesNotMasqueradeAsBadCredentialsAndLeavesContextEmpty() {
        when(mapper.existsById(1L)).thenThrow(new IllegalStateException("database unavailable"));
        BaseContext.setCurrentId(99L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(IllegalStateException.class, () -> interceptor.preHandle(request(token(1L)), response, handler));
        assertEquals(200, response.getStatus());
        assertNull(BaseContext.getCurrentId());
    }

    private String token(Long id) {
        return JwtUtil.createJWT(SIGNING_KEY, 60000L, Map.of("userId", id));
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/1");
        if (token != null) {
            request.addHeader("token", token);
        }
        return request;
    }
}
