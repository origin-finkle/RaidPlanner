package com.origin.auth;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;

@Component
public class OfficerApiInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Object authObject = session != null ? session.getAttribute(DiscordAuthService.SESSION_USER_KEY) : null;
        if (!(authObject instanceof DiscordUserSession)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "auth_required");
            return false;
        }

        DiscordUserSession user = (DiscordUserSession) authObject;
        if (!user.isOfficer()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "officer_role_required");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int status, String code) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }
}
