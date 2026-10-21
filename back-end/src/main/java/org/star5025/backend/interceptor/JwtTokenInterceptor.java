package org.star5025.backend.interceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.mapper.UserMapper;
import org.star5025.backend.properties.JwtProperties;
import org.star5025.backend.utils.JwtUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtTokenInterceptor implements AsyncHandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // A rejected request does not receive afterCompletion from this interceptor.
        BaseContext.removeCurrentId();
        if (!(handler instanceof HandlerMethod) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(jwtProperties.getTokenName());
        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Long userId;
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getSecretKey(), token);
            Object claimId = claims.get("userId");
            if (claimId == null || claims.getExpiration() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            userId = Long.valueOf(claimId.toString());
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // Deleting an account invalidates all its outstanding tokens. DB failures are not auth failures.
        if (!userMapper.existsById(userId)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        BaseContext.setCurrentId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        BaseContext.removeCurrentId();
    }
}
