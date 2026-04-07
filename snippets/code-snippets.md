```
/**
 * Unit tests for {@link EstreemNotificationControllerAdapter}.
 *
 * <p>This test suite validates the behavior of the notification endpoint:
 * <ul>
 *     <li>Successful request processing and EventFlow construction</li>
 *     <li>Token validation (issuer mandatory, subject optional)</li>
 *     <li>Payload validation (non-blank body)</li>
 *     <li>Flow ID extraction and validation from headers</li>
 *     <li>Fail-fast behavior when validation fails</li>
 * </ul>
 *
 * <p>Tests are organized by responsibility using nested classes to improve readability.
 */
class EstreemNotificationControllerAdapterTest {

    private static final String FLOW_ID = "flow_123";
    private static final String VALID_ISSUER = "ap12345";
    private static final String VALID_SUBJECT = "sub_123";
    private static final String PAYLOAD = "{\"event\":\"created\"}";

    private static final String INVALID_PAYLOAD_MESSAGE = "[GIL_004]The body received in the request is incorrect";
    private static final String INVALID_TOKEN_MISSING_ISSUER_MESSAGE = "[GIL_010]Invalid is mandatory... not present in the request";
    private static final String INVALID_TOKEN_ISSUER_REGEX_MESSAGE = "[GIL_010]Invalid issuer: is mandatory... do not match regex";
    private static final String INVALID_TOKEN_SUBJECT_REGEX_MESSAGE = "[GIL_010]Invalid Subject: is optional... but if present, it should match subject regex";
    private static final String FLOW_ID_MISSING_MESSAGE = "[GIL_005]The FlowId of the Event is missing in the request";
    private static final String FLOW_ID_MULTIPLE_MESSAGE = "[GIL_005]More than one FlowId is present in the httpheaders";
    private static final String FLOW_ID_INVALID_MESSAGE = "[GIL_005]The flowId does not respect constraints";

    private FromPartnerToBnppUseCase fromPartnerToBnppUseCase;
    private EstreemNotificationControllerAdapter adapter;

    @BeforeEach
    void setUp() {
        fromPartnerToBnppUseCase = mock(FromPartnerToBnppUseCase.class);
        adapter = new EstreemNotificationControllerAdapter(fromPartnerToBnppUseCase);
    }

    /**
     * Tests covering successful request processing.
     */
    @Nested
    class SuccessCases {

        /**
         * Verifies that a valid request produces a properly populated EventFlow
         * and returns HTTP 201.
         */
        @Test
        void notifications_shouldBuildEventFlowAndReturn201_whenRequestIsValid() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpHeaders headers = headersWithFlowId(FLOW_ID);
            TokenContext tokenContext = tokenContext(VALID_ISSUER, VALID_SUBJECT);

            long before = System.currentTimeMillis();

            ResponseEntity<Object> response = executeNotifications(request, PAYLOAD, headers, tokenContext);

            long after = System.currentTimeMillis();

            assertEquals(201, response.getStatusCode().value());

            ArgumentCaptor<EventFlow> captor = ArgumentCaptor.forClass(EventFlow.class);
            verify(fromPartnerToBnppUseCase).doPipeline(captor.capture());

            EventFlow eventFlow = captor.getValue();
            assertEquals(FLOW_ID, eventFlow.getFlowId());
            assertEquals(PAYLOAD, eventFlow.getPayload());
            assertEquals(FlowDirection.OUT, eventFlow.getFlowDirection());
            assertEquals(tokenContext, eventFlow.getTokenContext());
            assertEquals(List.of(FLOW_ID), eventFlow.getHeaders().get("flow_id"));

            assertTrue(eventFlow.getReceivedEventTimestamp() >= before);
            assertTrue(eventFlow.getReceivedEventTimestamp() <= after);
        }

        /**
         * Verifies that subject can be null while issuer is valid.
         */
        @Test
        void notifications_shouldBuildEventFlowAndReturn201_whenSubjectIsNull() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpHeaders headers = headersWithFlowId(FLOW_ID);
            TokenContext tokenContext = tokenContext(VALID_ISSUER, null);

            ResponseEntity<Object> response = executeNotifications(request, PAYLOAD, headers, tokenContext);

            assertEquals(201, response.getStatusCode().value());
            verify(fromPartnerToBnppUseCase).doPipeline(any(EventFlow.class));
        }
    }

    /**
     * Tests covering payload validation.
     */
    @Nested
    class PayloadValidation {

        /**
         * Verifies that blank payloads are rejected.
         */
        @ParameterizedTest
        @MethodSource("blankPayloadCases")
        void notifications_shouldThrowInvalidPayloadException_whenPayloadIsBlank(String payload) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpHeaders headers = headersWithFlowId(FLOW_ID);
            TokenContext tokenContext = tokenContext(VALID_ISSUER, VALID_SUBJECT);

            InvalidPayloadException exception = assertThrows(
                    InvalidPayloadException.class,
                    () -> executeNotifications(request, payload, headers, tokenContext)
            );

            assertEquals(INVALID_PAYLOAD_MESSAGE, exception.getMessage());
            verify(fromPartnerToBnppUseCase, never()).doPipeline(any());
        }
    }

    /**
     * Tests covering token validation rules.
     */
    @Nested
    class TokenValidation {

        /**
         * Verifies that invalid token combinations trigger the expected exception.
         */
        @ParameterizedTest
        @MethodSource("invalidTokenCases")
        void notifications_shouldThrowGilFlowException_whenTokenIsInvalid(String ignoredCaseName,
                                                                          TokenContext tokenContext,
                                                                          String expectedMessage) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpHeaders headers = headersWithFlowId(FLOW_ID);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> executeNotifications(request, PAYLOAD, headers, tokenContext)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            assertEquals(expectedMessage, exception.getMessage());
            verify(fromPartnerToBnppUseCase, never()).doPipeline(any());
        }
    }

    /**
     * Tests covering flow_id extraction and validation.
     */
    @Nested
    class FlowIdValidation {

        /**
         * Verifies behavior when flow_id header is missing.
         */
        @Test
        void notifications_shouldThrowNullPointerException_whenFlowIdHeaderIsMissing() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpHeaders headers = new HttpHeaders();
            TokenContext tokenContext = tokenContext(VALID_ISSUER, VALID_SUBJECT);

            assertThrows(
                    NullPointerException.class,
                    () -> executeNotifications(request, PAYLOAD, headers, tokenContext)
            );

            verify(fromPartnerToBnppUseCase, never()).doPipeline(any());
        }

        /**
         * Verifies invalid flow_id header scenarios.
         */
        @ParameterizedTest
        @MethodSource("invalidFlowIdHeaderCases")
        void notifications_shouldThrowFlowIdResolutionException_whenFlowIdIsInvalid(String ignoredCaseName,
                                                                                    HttpHeaders headers,
                                                                                    String expectedMessage) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            TokenContext tokenContext = tokenContext(VALID_ISSUER, VALID_SUBJECT);

            FlowIdResolutionException exception = assertThrows(
                    FlowIdResolutionException.class,
                    () -> executeNotifications(request, PAYLOAD, headers, tokenContext)
            );

            assertEquals(expectedMessage, exception.getMessage());
            verify(fromPartnerToBnppUseCase, never()).doPipeline(any());
        }
    }

    static Stream<String> blankPayloadCases() {
        return Stream.of("", "   ", "\n", "\t");
    }

    static Stream<Arguments> invalidTokenCases() {
        return Stream.of(
                Arguments.of("issuer is null", tokenContext(null, VALID_SUBJECT), INVALID_TOKEN_MISSING_ISSUER_MESSAGE),
                Arguments.of("issuer too short", tokenContext("ab", VALID_SUBJECT), INVALID_TOKEN_ISSUER_REGEX_MESSAGE),
                Arguments.of("issuer invalid char", tokenContext("ab@", VALID_SUBJECT), INVALID_TOKEN_ISSUER_REGEX_MESSAGE),
                Arguments.of("subject invalid char", tokenContext(VALID_ISSUER, "##"), INVALID_TOKEN_SUBJECT_REGEX_MESSAGE)
        );
    }

    static Stream<Arguments> invalidFlowIdHeaderCases() {
        HttpHeaders empty = new HttpHeaders();
        empty.put("flow_id", List.of());

        HttpHeaders multiple = new HttpHeaders();
        multiple.add("flow_id", "flow_123");
        multiple.add("flow_id", "flow_456");

        HttpHeaders nullValue = new HttpHeaders();
        nullValue.put("flow_id", Collections.singletonList(null));

        return Stream.of(
                Arguments.of("empty flow_id", empty, FLOW_ID_MISSING_MESSAGE),
                Arguments.of("multiple flow_id", multiple, FLOW_ID_MULTIPLE_MESSAGE),
                Arguments.of("null flow_id", nullValue, FLOW_ID_INVALID_MESSAGE),
                Arguments.of("invalid regex", headersWithFlowId("bad"), FLOW_ID_INVALID_MESSAGE)
        );
    }

    private ResponseEntity<Object> executeNotifications(HttpServletRequest request,
                                                        String body,
                                                        HttpHeaders headers,
                                                        TokenContext tokenContext) {
        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);
            return adapter.notifications(request, body, headers);
        }
    }

    private static TokenContext tokenContext(String issuer, String subject) {
        return TokenContext.builder()
                .issuer(issuer)
                .subject(subject)
                .build();
    }

    private static HttpHeaders headersWithFlowId(String flowId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("flow_id", flowId);
        return headers;
    }
}
```


MR / PR summary

Enhanced unit tests for EstreemApiGatewayAdapter and ApiFlowProcessorStrategyImpl to improve readability, maintainability, and branch coverage.

Main changes
•	aligned tests with the current token validation rules:
•	ISS mandatory
•	SUB optional
•	regex updated to the new generic format
•	replaced outdated invalid token samples with cases that truly fail the new regex
•	added positive coverage for SUB = null
•	added coverage for request/response payload empty and null branches
•	added assertions on mapped ApiFlow content in adapter tests
•	added coverage for adapter delegation methods
•	updated strategy tests to match current config validation behavior:
•	authorizedCodeAp == null || empty now raises CORE_CONFIG_MALFORMED
•	simplified strategy test setup by using real domain fixtures instead of mocking all model objects
•	strengthened assertions on transformation contexts and call ordering
•	reduced duplication through helper methods and parameterized scenarios

Benefits
•	better alignment between tests and current implementation
•	improved branch coverage
•	clearer test intent
•	easier future maintenance and refactoring

⸻

5) Suggested commit message

A few options depending on style.





