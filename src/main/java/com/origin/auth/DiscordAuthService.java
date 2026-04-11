package com.origin.auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpSession;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordAuthService {

    public static final String SESSION_USER_KEY = "DISCORD_AUTH_USER";
    private static final String SESSION_STATE_KEY = "DISCORD_AUTH_STATE";
    private static final String DISCORD_API_BASE = "https://discord.com/api";

    @Value("${discord.oauth.client-id:}")
    private String clientId;

    @Value("${discord.oauth.client-secret:}")
    private String clientSecret;

    @Value("${discord.oauth.redirect-uri:http://localhost:8080/api/auth/discord/callback}")
    private String redirectUri;

    @Value("${discord.oauth.frontend-success-url:http://localhost:4200/raids}")
    private String frontendSuccessUrl;

    @Value("${discord.oauth.frontend-denied-url:http://localhost:4200/login?denied=1}")
    private String frontendDeniedUrl;

    @Value("${discord.guild.id:670711708628811805}")
    private String guildId;

    private final JDA jda;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    public String buildAuthorizationUrl(HttpSession session) {
        assertConfigured();

        String state = UUID.randomUUID().toString();
        session.setAttribute(SESSION_STATE_KEY, state);

        return UriComponentsBuilder
                .fromHttpUrl(DISCORD_API_BASE + "/oauth2/authorize")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "identify guilds.members.read")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public String handleCallback(String code, String state, HttpSession session) {
        assertConfigured();
        validateState(state, session);

        DiscordTokenResponse token = exchangeCodeForToken(code);
        DiscordUserResponse user = fetchCurrentUser(token.getAccessToken());
        DiscordGuildMemberResponse member = fetchGuildMember(token.getAccessToken());

        boolean officer = isOfficer(member);
        if (!officer) {
            session.removeAttribute(SESSION_USER_KEY);
            return frontendDeniedUrl;
        }

        String displayName = StringUtils.hasText(member.getNick()) ? member.getNick() : user.getUsername();
        session.setAttribute(
                SESSION_USER_KEY,
                new DiscordUserSession(user.getId(), user.getUsername(), displayName, true)
        );
        return frontendSuccessUrl;
    }

    public String getFrontendLoginUrlWithFlag(String flag) {
        return UriComponentsBuilder
                .fromHttpUrl(getFrontendLoginUrl())
                .queryParam(flag, 1)
                .build(true)
                .toUriString();
    }

    public DiscordUserSession getCurrentUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER_KEY);
        if (value instanceof DiscordUserSession) {
            return (DiscordUserSession) value;
        }
        return null;
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private String getFrontendLoginUrl() {
        return UriComponentsBuilder.fromHttpUrl(frontendDeniedUrl)
                .replaceQuery(null)
                .build(true)
                .toUriString();
    }

    private void validateState(String state, HttpSession session) {
        Object expected = session.getAttribute(SESSION_STATE_KEY);
        session.removeAttribute(SESSION_STATE_KEY);

        if (!(expected instanceof String) || !expected.equals(state)) {
            throw new IllegalStateException("Etat OAuth Discord invalide");
        }
    }

    private DiscordTokenResponse exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        try {
            ResponseEntity<DiscordTokenResponse> response = restTemplate.exchange(
                    DISCORD_API_BASE + "/oauth2/token",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    DiscordTokenResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            log.error("Echec de l'echange OAuth Discord: {}", exception.getResponseBodyAsString());
            throw exception;
        }
    }

    private DiscordUserResponse fetchCurrentUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(
                DISCORD_API_BASE + "/users/@me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                DiscordUserResponse.class
        ).getBody();
    }

    private DiscordGuildMemberResponse fetchGuildMember(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            return restTemplate.exchange(
                    DISCORD_API_BASE + "/users/@me/guilds/" + guildId + "/member",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    DiscordGuildMemberResponse.class
            ).getBody();
        } catch (HttpStatusCodeException exception) {
            log.error("Impossible de recuperer le membre Discord dans la guilde {}: {}", guildId, exception.getResponseBodyAsString());
            throw exception;
        }
    }

    private boolean isOfficer(DiscordGuildMemberResponse member) {
        if (member == null || member.getRoles() == null || member.getRoles().isEmpty()) {
            return false;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            log.warn("Guilde Discord introuvable pour l'authentification: {}", guildId);
            return false;
        }

        Set<String> officerRoleIds = guild.getRoles().stream()
                .filter(role -> normalizeRoleName(role.getName()).equals("officiers"))
                .map(role -> role.getId())
                .collect(Collectors.toSet());

        if (officerRoleIds.isEmpty()) {
            log.warn("Aucun role 'Officiers' trouve dans la guilde Discord {}", guildId);
            return false;
        }

        return member.getRoles().stream().anyMatch(officerRoleIds::contains);
    }

    private void assertConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("OAuth Discord non configure: client-id/client-secret manquants");
        }
    }

    private String normalizeRoleName(String roleName) {
        return Normalizer.normalize(roleName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    @Data
    private static class DiscordTokenResponse {
        private String access_token;
        private String token_type;
        private String refresh_token;
        private String scope;
        private Integer expires_in;

        public String getAccessToken() {
            return access_token;
        }
    }

    @Data
    private static class DiscordUserResponse {
        private String id;
        private String username;
    }

    @Data
    private static class DiscordGuildMemberResponse {
        private String nick;
        private List<String> roles;
    }
}
