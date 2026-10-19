package com.paynest.common.config;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OpenApiContractIntegrationTests {

    private static final String BEARER_AUTH = "bearerAuth";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private JsonNode document;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();
        document = objectMapper.reader(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    void anonymousDocumentationAccessDoesNotRelaxProtectedProductRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stray-placeholder"))
                .andExpect(status().isOk());

        for (String path : List.of(
                "/api/users/me",
                "/api/wallet",
                "/api/transactions",
                "/api/transactions/1")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void documentContainsExactlyTheSevenApprovedTaggedOperations() {
        JsonNode paths = document.get("paths");
        assertThat(paths.size()).isEqualTo(7);

        assertOperation("post", "/api/auth/signup", "Authentication", "201");
        assertOperation("post", "/api/auth/signin", "Authentication", "200");
        assertOperation("get", "/api/users/me", "User", "200");
        assertOperation("get", "/api/wallet", "Wallet", "200");
        assertOperation("post", "/api/wallet/transfers", "Wallet", "201");
        assertOperation("get", "/api/transactions", "Transactions", "200");
        assertOperation("get", "/api/transactions/{id}", "Transactions", "200");

        assertThat(paths.get("/api/auth/signup").size()).isOne();
        assertThat(paths.get("/api/auth/signin").size()).isOne();
        assertThat(paths.get("/api/users/me").size()).isOne();
        assertThat(paths.get("/api/wallet").size()).isOne();
        assertThat(paths.get("/api/wallet/transfers").size()).isOne();
        assertThat(paths.get("/api/transactions").size()).isOne();
        assertThat(paths.get("/api/transactions/{id}").size()).isOne();
    }

    @Test
    void bearerAuthenticationIsDeclaredOnlyForTheFiveProtectedOperations() {
        JsonNode scheme = document.get("components").get("securitySchemes").get(BEARER_AUTH);
        assertThat(scheme.get("type").asString()).isEqualTo("http");
        assertThat(scheme.get("scheme").asString()).isEqualTo("bearer");
        assertThat(scheme.get("bearerFormat").asString()).isEqualTo("JWT");
        assertThat(document.get("security")).isNull();

        assertPublic(operation("post", "/api/auth/signup"));
        assertPublic(operation("post", "/api/auth/signin"));
        assertProtected(operation("get", "/api/users/me"));
        assertProtected(operation("get", "/api/wallet"));
        assertProtected(operation("post", "/api/wallet/transfers"));
        assertProtected(operation("get", "/api/transactions"));
        assertProtected(operation("get", "/api/transactions/{id}"));
    }

    @Test
    void inputConstraintsPaginationAndPositiveBigintIdAreExplicit() {
        assertRequiredRequestBody("post", "/api/auth/signup", "SignUpRequest");
        assertRequiredRequestBody("post", "/api/auth/signin", "SignInRequest");
        assertRequiredRequestBody("post", "/api/wallet/transfers", "TransferRequest");

        assertParameters("post", "/api/auth/signup");
        assertParameters("post", "/api/auth/signin");
        assertParameters("get", "/api/users/me");
        assertParameters("get", "/api/wallet");
        assertParameters("post", "/api/wallet/transfers");
        assertParameters("get", "/api/transactions", "page:query", "size:query");
        assertParameters("get", "/api/transactions/{id}", "id:path");

        JsonNode signUp = schema("SignUpRequest");
        assertPropertiesAndRequired(signUp,
                List.of("name", "email", "password"),
                List.of("name", "email", "password"));
        assertStringLimits(signUp, "name", 2, 100);
        assertThat(signUp.get("properties").get("email").get("format").asString()).isEqualTo("email");
        assertThat(signUp.get("properties").get("email").get("maxLength").asInt()).isEqualTo(254);
        assertStringLimits(signUp, "password", 8, 72);
        assertCredential(signUp.get("properties").get("password"));

        JsonNode signIn = schema("SignInRequest");
        assertPropertiesAndRequired(signIn,
                List.of("email", "password"),
                List.of("email", "password"));
        assertThat(signIn.get("properties").get("email").get("format").asString()).isEqualTo("email");
        assertThat(signIn.get("properties").get("email").get("maxLength").asInt()).isEqualTo(254);
        assertThat(signIn.get("properties").get("email").get("description").asString())
                .contains("Unicode whitespace", "lowercase");
        assertThat(signIn.get("properties").get("password").get("minLength").asInt()).isEqualTo(1);
        assertCredential(signIn.get("properties").get("password"));

        JsonNode transfer = schema("TransferRequest");
        assertPropertiesAndRequired(transfer,
                List.of("recipientEmail", "amount"),
                List.of("recipientEmail", "amount"));
        assertThat(transfer.get("properties").get("recipientEmail").get("format").asString())
                .isEqualTo("email");
        assertThat(transfer.get("properties").get("recipientEmail").get("description").asString())
                .contains("Unicode whitespace", "lowercase");
        assertThat(transfer.get("properties").get("amount").get("description").asString())
                .contains("more than two fractional digits", "25.500", "rejected");
        assertMoney(transfer.get("properties").get("amount"), true);

        JsonNode page = parameter("/api/transactions", "page");
        assertThat(page.get("required").asBoolean()).isFalse();
        assertThat(page.get("schema").get("type").asString()).isEqualTo("integer");
        assertThat(page.get("schema").get("format").asString()).isEqualTo("int32");
        assertThat(page.get("schema").get("default").asInt()).isZero();
        assertThat(page.get("schema").get("minimum").asInt()).isZero();

        JsonNode size = parameter("/api/transactions", "size");
        assertThat(size.get("schema").get("default").asInt()).isEqualTo(20);
        assertThat(size.get("schema").get("minimum").asInt()).isEqualTo(1);
        assertThat(size.get("schema").get("maximum").asInt()).isEqualTo(100);

        JsonNode id = parameter("/api/transactions/{id}", "id");
        assertThat(id.get("required").asBoolean()).isTrue();
        assertThat(id.get("schema").get("type").asString()).isEqualTo("integer");
        assertThat(id.get("schema").get("format").asString()).isEqualTo("int64");
        assertThat(id.get("schema").get("minimum").asInt()).isEqualTo(1);
    }

    @Test
    void successAndRepresentativeFailureResponsesReferenceOnlyApiDtos() {
        assertResponse("post", "/api/auth/signup", "201", "SignUpResponse");
        assertResponse("post", "/api/auth/signin", "200", "TokenResponse");
        assertResponse("get", "/api/users/me", "200", "UserResponse");
        assertResponse("get", "/api/wallet", "200", "WalletResponse");
        assertResponse("post", "/api/wallet/transfers", "201", "TransferResponse");
        assertResponse("get", "/api/transactions", "200", "TransactionHistoryPageResponse");
        assertResponse("get", "/api/transactions/{id}", "200", "TransactionDetailResponse");

        assertErrors("post", "/api/auth/signup", "400", "409", "500");
        assertErrors("post", "/api/auth/signin", "400", "401");
        assertErrors("get", "/api/users/me", "401");
        assertErrors("get", "/api/wallet", "401", "500");
        assertErrors("post", "/api/wallet/transfers", "400", "401", "404", "409", "422", "500");
        assertErrors("get", "/api/transactions", "400", "401", "404");
        assertErrors("get", "/api/transactions/{id}", "400", "401", "404");

        JsonNode apiError = schema("ApiError");
        assertPropertiesAndRequired(apiError,
                List.of("timestamp", "status", "error", "message", "path", "fieldErrors"),
                List.of("timestamp", "status", "error", "message", "path"));
        assertThat(apiError.get("properties").get("fieldErrors").get("additionalProperties")
                .get("type").asString()).isEqualTo("string");

        assertErrorExample("post", "/api/auth/signup", "409", "duplicateEmail",
                409, "EMAIL_ALREADY_EXISTS", "/api/auth/signup");
        assertErrorExample("post", "/api/auth/signin", "401", "invalidCredentials",
                401, "AUTHENTICATION_FAILED", "/api/auth/signin");
        assertErrorExample("get", "/api/users/me", "401", "invalidToken",
                401, "INVALID_TOKEN", "/api/users/me");
        assertErrorExample("get", "/api/wallet", "401", "invalidToken",
                401, "INVALID_TOKEN", "/api/wallet");
        assertErrorExample("post", "/api/wallet/transfers", "401", "invalidToken",
                401, "INVALID_TOKEN", "/api/wallet/transfers");
        assertErrorExample("post", "/api/wallet/transfers", "409", "concurrencyConflict",
                409, "CONFLICT", "/api/wallet/transfers");
        assertErrorExample("post", "/api/wallet/transfers", "422", "businessRejection",
                422, "SELF_TRANSFER_NOT_ALLOWED", "/api/wallet/transfers");
        assertErrorExample("get", "/api/transactions", "401", "invalidToken",
                401, "INVALID_TOKEN", "/api/transactions");
        assertErrorExample("get", "/api/transactions/{id}", "401", "invalidToken",
                401, "INVALID_TOKEN", "/api/transactions/42");
    }

    @Test
    void examplesAreInventedAndEveryMoneyPropertyUsesDecimalTwoFractionSemantics() {
        assertThat(schema("SignUpRequest").toString())
                .contains("ada.payne@example.test", "correct-horse-placeholder")
                .doesNotContain("jwt", "secret", "hash");
        assertThat(schema("TokenResponse").toString())
                .contains("placeholder.access.token")
                .doesNotContain("eyJ");

        assertMoney(schema("WalletResponse").get("properties").get("balance"), false);
        assertMoney(schema("TransferResponse").get("properties").get("balanceAfter"), false);
        assertMoney(schema("TransferTransactionDetails").get("properties").get("amount"), false);
        assertMoney(schema("TransactionHistoryItemResponse").get("properties").get("amount"), false);
        assertMoney(schema("TransactionDetailResponse").get("properties").get("amount"), false);

        assertThat(document.get("components").get("schemas").propertyNames())
                .containsExactlyInAnyOrder(
                        "ApiError",
                        "SignInRequest",
                        "SignUpRequest",
                        "SignUpResponse",
                        "TokenResponse",
                        "UserResponse",
                        "WalletResponse",
                        "TransferRequest",
                        "TransferCounterparty",
                        "TransferResponse",
                        "TransferTransactionDetails",
                        "TransactionCounterparty",
                        "TransactionHistoryItemResponse",
                        "TransactionHistoryPageResponse",
                        "TransactionDetailResponse",
                        "TransactionParticipant");
        assertThat(document.get("components").get("schemas").toString())
                .doesNotContain("passwordHash", "senderWallet", "receiverWallet", "claims");

        assertResponseSchemaShapes();
    }

    private void assertResponseSchemaShapes() {
        assertPropertiesAndRequired(schema("SignUpResponse"),
                List.of("user", "wallet"), List.of("user", "wallet"));
        assertPropertyRef("SignUpResponse", "user", "UserResponse");
        assertPropertyRef("SignUpResponse", "wallet", "WalletResponse");

        assertPropertiesAndRequired(schema("TokenResponse"),
                List.of("accessToken", "tokenType", "expiresAt"),
                List.of("accessToken", "tokenType", "expiresAt"));
        assertDateTime("TokenResponse", "expiresAt");

        assertPropertiesAndRequired(schema("UserResponse"),
                List.of("id", "name", "email", "createdAt"),
                List.of("id", "name", "email", "createdAt"));
        assertDateTime("UserResponse", "createdAt");

        assertPropertiesAndRequired(schema("WalletResponse"),
                List.of("balance", "currency", "createdAt"),
                List.of("balance", "currency", "createdAt"));
        assertEnum("WalletResponse", "currency", "USD");
        assertDateTime("WalletResponse", "createdAt");

        assertPropertiesAndRequired(schema("TransferResponse"),
                List.of("transaction", "balanceAfter"), List.of("transaction", "balanceAfter"));
        assertPropertyRef("TransferResponse", "transaction", "TransferTransactionDetails");
        assertPropertiesAndRequired(schema("TransferTransactionDetails"),
                List.of("id", "direction", "counterparty", "amount", "currency", "status", "createdAt"),
                List.of("id", "direction", "counterparty", "amount", "currency", "status", "createdAt"));
        assertPropertyRef("TransferTransactionDetails", "counterparty", "TransferCounterparty");
        assertEnum("TransferTransactionDetails", "direction", "SENT");
        assertEnum("TransferTransactionDetails", "currency", "USD");
        assertEnum("TransferTransactionDetails", "status", "COMPLETED");
        assertDateTime("TransferTransactionDetails", "createdAt");
        assertNamedPair("TransferCounterparty");

        assertPropertiesAndRequired(schema("TransactionHistoryPageResponse"),
                List.of("content", "page", "size", "totalElements", "totalPages", "last"),
                List.of("content", "page", "size", "totalElements", "totalPages", "last"));
        assertThat(schema("TransactionHistoryPageResponse").get("properties").get("content")
                .get("items").get("$ref").asString())
                .isEqualTo("#/components/schemas/TransactionHistoryItemResponse");
        assertPropertiesAndRequired(schema("TransactionHistoryItemResponse"),
                List.of("id", "direction", "counterparty", "amount", "currency", "status", "createdAt"),
                List.of("id", "direction", "counterparty", "amount", "currency", "status", "createdAt"));
        assertPropertyRef("TransactionHistoryItemResponse", "counterparty", "TransactionCounterparty");
        assertEnum("TransactionHistoryItemResponse", "direction", "SENT", "RECEIVED");
        assertEnum("TransactionHistoryItemResponse", "currency", "USD");
        assertEnum("TransactionHistoryItemResponse", "status", "COMPLETED");
        assertDateTime("TransactionHistoryItemResponse", "createdAt");
        assertNamedPair("TransactionCounterparty");

        assertPropertiesAndRequired(schema("TransactionDetailResponse"),
                List.of("id", "direction", "sender", "recipient", "amount", "currency", "status", "createdAt"),
                List.of("id", "direction", "sender", "recipient", "amount", "currency", "status", "createdAt"));
        assertPropertyRef("TransactionDetailResponse", "sender", "TransactionParticipant");
        assertPropertyRef("TransactionDetailResponse", "recipient", "TransactionParticipant");
        assertEnum("TransactionDetailResponse", "direction", "SENT", "RECEIVED");
        assertEnum("TransactionDetailResponse", "currency", "USD");
        assertEnum("TransactionDetailResponse", "status", "COMPLETED");
        assertDateTime("TransactionDetailResponse", "createdAt");
        assertNamedPair("TransactionParticipant");
        assertDateTime("ApiError", "timestamp");
    }

    private void assertOperation(String method, String path, String tag, String successStatus) {
        JsonNode operation = operation(method, path);
        assertThat(operation).isNotNull();
        assertThat(operation.get("tags").get(0).asString()).isEqualTo(tag);
        assertThat(operation.get("summary").asString()).isNotBlank();
        assertThat(operation.get("responses").get(successStatus)).isNotNull();
    }

    private void assertResponse(String method, String path, String status, String schemaName) {
        assertSchemaRef(operation(method, path).get("responses").get(status), schemaName);
    }

    private void assertRequiredRequestBody(String method, String path, String schemaName) {
        JsonNode requestBody = operation(method, path).get("requestBody");
        assertThat(requestBody.get("required").asBoolean()).isTrue();
        assertSchemaRef(requestBody, schemaName);
    }

    private void assertErrors(String method, String path, String... statuses) {
        JsonNode responses = operation(method, path).get("responses");
        for (String status : statuses) {
            assertThat(responses.get(status)).as(method + " " + path + " " + status).isNotNull();
            assertSchemaRef(responses.get(status), "ApiError");
        }
    }

    private static void assertSchemaRef(JsonNode container, String schemaName) {
        JsonNode schema = container.get("content").get("application/json").get("schema");
        assertThat(schema.get("$ref").asString())
                .isEqualTo("#/components/schemas/" + schemaName);
    }

    private void assertErrorExample(
            String method,
            String path,
            String status,
            String exampleName,
            int expectedStatus,
            String expectedError,
            String expectedPath
    ) {
        JsonNode value = operation(method, path).get("responses").get(status)
                .get("content").get("application/json").get("examples").get(exampleName).get("value");
        assertThat(value.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(value.get("error").asString()).isEqualTo(expectedError);
        assertThat(value.get("path").asString()).isEqualTo(expectedPath);
        assertThat(value.get("timestamp").asString()).isEqualTo("2026-10-19T12:10:00Z");
        assertThat(value.get("message").asString()).isNotBlank();
        assertThat(value.toString()).doesNotContain(
                "password", "secret", "hash", "eyJ", "sql", "stackTrace");
    }

    private static void assertPublic(JsonNode operation) {
        assertThat(operation.get("security")).isNull();
    }

    private static void assertProtected(JsonNode operation) {
        JsonNode security = operation.get("security");
        assertThat(security).hasSize(1);
        assertThat(security.get(0).get(BEARER_AUTH)).isNotNull();
    }

    private static void assertStringLimits(JsonNode owner, String property, int minimum, int maximum) {
        JsonNode value = owner.get("properties").get(property);
        assertThat(value.get("minLength").asInt()).isEqualTo(minimum);
        assertThat(value.get("maxLength").asInt()).isEqualTo(maximum);
    }

    private static void assertCredential(JsonNode credential) {
        assertThat(credential.get("format").asString()).isEqualTo("password");
        assertThat(credential.get("writeOnly").asBoolean()).isTrue();
    }

    private static void assertPropertiesAndRequired(
            JsonNode owner,
            List<String> properties,
            List<String> required
    ) {
        assertThat(owner.get("properties").propertyNames()).containsExactlyInAnyOrderElementsOf(properties);
        assertThat(owner.get("required").values())
                .extracting(JsonNode::asString)
                .containsExactlyInAnyOrderElementsOf(required);
    }

    private void assertPropertyRef(String schemaName, String property, String referencedSchema) {
        assertThat(schema(schemaName).get("properties").get(property).get("$ref").asString())
                .isEqualTo("#/components/schemas/" + referencedSchema);
    }

    private void assertEnum(String schemaName, String property, String... values) {
        assertThat(schema(schemaName).get("properties").get(property).get("enum").values())
                .extracting(JsonNode::asString)
                .containsExactlyInAnyOrder(values);
    }

    private void assertDateTime(String schemaName, String property) {
        assertThat(schema(schemaName).get("properties").get(property).get("format").asString())
                .isEqualTo("date-time");
    }

    private void assertNamedPair(String schemaName) {
        assertPropertiesAndRequired(schema(schemaName),
                List.of("name", "email"), List.of("name", "email"));
    }

    private static void assertMoney(JsonNode money, boolean requestAmount) {
        assertThat(money.get("type").asString()).isEqualTo("number");
        assertThat(money.get("format").asString()).isEqualTo("decimal");
        assertThat(money.get("multipleOf").decimalValue()).isEqualByComparingTo("0.01");
        assertThat(money.get("example").decimalValue().remainder(new java.math.BigDecimal("0.01")))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
        if (requestAmount) {
            assertThat(money.get("minimum").decimalValue()).isEqualByComparingTo("0.01");
            assertThat(money.get("maximum").decimalValue())
                    .isEqualByComparingTo("99999999999999999.99");
        }
    }

    private JsonNode parameter(String path, String name) {
        for (JsonNode parameter : operation("get", path).get("parameters")) {
            if (name.equals(parameter.get("name").asString())) {
                return parameter;
            }
        }
        throw new AssertionError("Missing parameter " + name + " on " + path);
    }

    private void assertParameters(String method, String path, String... expected) {
        JsonNode parameters = operation(method, path).get("parameters");
        if (expected.length == 0) {
            assertThat(parameters == null || parameters.isEmpty()).isTrue();
            return;
        }
        assertThat(parameters).hasSize(expected.length);
        for (String specification : expected) {
            String[] parts = specification.split(":", 2);
            JsonNode parameter = null;
            for (JsonNode candidate : parameters) {
                if (parts[0].equals(candidate.get("name").asString())) {
                    parameter = candidate;
                    break;
                }
            }
            assertThat(parameter).as(method + " " + path + " parameter " + parts[0]).isNotNull();
            assertThat(parameter.get("in").asString()).isEqualTo(parts[1]);
        }
        assertThat(parameters.toString()).doesNotContain("AuthenticatedUser", "principal", "userId");
    }

    private JsonNode operation(String method, String path) {
        return document.get("paths").get(path).get(method);
    }

    private JsonNode schema(String name) {
        JsonNode schema = schemaOrNull(name);
        assertThat(schema).as(name).isNotNull();
        return schema;
    }

    private JsonNode schemaOrNull(String name) {
        return document.get("components").get("schemas").get(name);
    }
}
