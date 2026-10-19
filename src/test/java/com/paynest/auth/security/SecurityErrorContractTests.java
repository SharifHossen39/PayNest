package com.paynest.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.paynest.common.error.ApiErrorResponseWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.firewall.RequestRejectedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SecurityErrorContractTests {

    private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    @Test
    void authenticationEntryPointReturnsInvalidTokenWithBearerChallengeAndSafeLog(
            CapturedOutput output
    ) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(
                writer(objectMapper));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/wallet");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new BadCredentialsException("sensitive token parser detail"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("timestamp").asString()).isEqualTo(NOW.toString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asString()).isEqualTo("INVALID_TOKEN");
        assertThat(body.get("message").asString()).isEqualTo("Authentication token is invalid");
        assertThat(body.get("path").asString()).isEqualTo("/api/wallet");
        assertThat(response.getContentAsString()).doesNotContain("sensitive token parser detail");
        assertThat(output.getAll())
                .contains("event=authentication outcome=failure category=invalid_token")
                .doesNotContain("sensitive token parser detail", "/api/wallet");
    }

    @Test
    void accessDeniedHandlerReturnsSharedForbiddenEnvelopeWithoutBearerChallenge(
            CapturedOutput output
    ) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(writer(objectMapper));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/wallet");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("sensitive policy detail"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asString()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("message").asString()).isEqualTo("Access denied");
        assertThat(body.get("path").asString()).isEqualTo("/api/wallet");
        assertThat(response.getContentAsString()).doesNotContain("sensitive policy detail");
        assertThat(output.getAll())
                .contains("event=authorization outcome=denied category=access_denied")
                .doesNotContain("sensitive policy detail", "/api/wallet");
    }

    @Test
    void rejectedRequestHandlerReturnsSafeValidationEnvelopeWithoutFirewallDetails(
            CapturedOutput output
    ) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonRequestRejectedHandler handler = new JsonRequestRejectedHandler(writer(objectMapper));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/wallet;jsessionid=private-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                request,
                response,
                new RequestRejectedException("private firewall parser detail"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("error").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("message").asString()).isEqualTo("Request validation failed");
        assertThat(body.get("path").asString()).isEqualTo("/api/wallet");
        assertThat(response.getContentAsString())
                .doesNotContain("private firewall parser detail", "private-value", "jsessionid");
        assertThat(output.getAll())
                .contains("event=request outcome=rejected category=invalid_syntax")
                .doesNotContain("private firewall parser detail", "private-value");
    }

    private static ApiErrorResponseWriter writer(ObjectMapper objectMapper) {
        return new ApiErrorResponseWriter(Clock.fixed(NOW, ZoneOffset.UTC), objectMapper);
    }
}
