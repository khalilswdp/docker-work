```

@RequestMapping(EstreemNotificationControllerAdapter.API_GATEWAY_ESTREEM_PATH_PREFIX)
@RestController
@RequiredArgsConstructor
public class EstreemNotificationControllerAdapter {

    public static final String API_GATEWAY_ESTREEM_PATH_PREFIX = "/bcef/v1/notifications";

    private static final String FLOW_ID_HTTP_HEADER_KEY = "flow_id";

    private static final String FLOW_ID_REGEX = "^[a-zA-Z0-9_-]{5,15}$";
    public static final String ESTREEM_ISS_REGEX = "^[A-Za-z0-9_\\-/]{3,15}$";
    public static final String ESTREEM_SUB_REGEX = "^[A-Za-z0-9_\\-/]{3,15}$";

    private final FromPartnerToBnppUseCase fromPartnerToBnppUseCase;

    @PostMapping(value = "/**")
    public ResponseEntity<Object> notifications(HttpServletRequest request, @RequestBody String body, @RequestHeader HttpHeaders httpHeaders) {
        Map<String, List<String>> convertedHttpHeaders = HttpHeadersUtil.fromHttpHeaders(httpHeaders);

        TokenContext requestTokenContext = JwtTokenUtil.getTokenContext(request);

        validToken(requestTokenContext);

        String flowId = this.extractFlowIdFromHeaders(convertedHttpHeaders);

        if (!isPayloadCorrect(body)) {
            throw new InvalidPayloadException("The body received in the request is incorrect");
        }

        EventFlow eventFlow = EventFlow.builder()
                .flowId(flowId)
                .receivedEventTimestamp(this.getCurrentTimestamp())
                .headers(convertedHttpHeaders)
                .payload(body)
                .flowDirection(FlowDirection.OUT)
                .tokenContext(requestTokenContext)
                .build();

        this.fromPartnerToBnppUseCase.doPipeline(eventFlow);

        return ResponseEntity.status(201).build();
    }

    private long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }

    private String extractFlowIdFromHeaders(Map<String, List<String>> httpHeaders) {
        Iterator<String> iteratorString = httpHeaders.get(FLOW_ID_HTTP_HEADER_KEY).iterator();

        if (!iteratorString.hasNext()) {
            throw new FlowIdResolutionException("The FlowId of the Event is missing in the request");
        }

        final String flowId = iteratorString.next();

        if (iteratorString.hasNext()) {
            throw new FlowIdResolutionException("More than one FlowId is present in the httpheaders");
        }

        if (!this.isFlowIdCorrect(flowId)) {
            throw new FlowIdResolutionException("The flowId does not respect constraints");
        }

        return flowId;
    }

    private boolean isFlowIdCorrect(String flowId) {
        return flowId != null && flowId.matches(FLOW_ID_REGEX);
    }

    private boolean isPayloadCorrect(String content) {
        return !content.isBlank();
    }

    /**
     * Validates the token context extracted from the request.
     *
     * <p>Validation rules:
     * <ul>
     *     <li>{@code issuer} is mandatory</li>
     *     <li>{@code issuer} must match {@link #ESTREEM_ISS_REGEX}</li>
     *     <li>{@code subject} is optional</li>
     *     <li>if present, {@code subject} must match {@link #ESTREEM_SUB_REGEX}</li>
     * </ul>
     *
     * @param requestTokenContext token context extracted from the request
     *
     * @throws GilFlowException if issuer is missing or if issuer/subject do not match expected format
     */
    private static void validToken(TokenContext requestTokenContext) {
        Pattern issuerPattern = Pattern.compile(ESTREEM_ISS_REGEX);

        String issuer = requestTokenContext.issuer();
        String subject = requestTokenContext.subject();

        if (issuer == null) {
            throw new GilFlowException(GilErrorCode.INVALID_TOKEN, "Invalid is mandatory... not present in the request");
        }

        Matcher issuerMatcher = issuerPattern.matcher(issuer);
        if (!issuerMatcher.matches()) {
            throw new GilFlowException(GilErrorCode.INVALID_TOKEN, "Invalid issuer: is mandatory... do not match regex");
        }

        if (subject != null) {
            Pattern subjectPattern = Pattern.compile(ESTREEM_SUB_REGEX);
            Matcher subjectMatcher = subjectPattern.matcher(subject);
            if (!subjectMatcher.matches()) {
                throw new GilFlowException(GilErrorCode.INVALID_TOKEN, "Invalid Subject: is optional... but if present, it should match subject regex");
            }

        }
    }

}




```

```
/**
 * Unit tests for {@link EstreemNotificationControllerAdapter}.
 *
 * <p>This test suite validates the behavior of the notification endpoint:
 * <ul>
 *     <li>successful request processing and {@link EventFlow} construction</li>
 *     <li>token validation where issuer is mandatory and subject is optional</li>
 *     <li>payload validation for non-blank request bodies</li>
 *     <li>flow id extraction and validation from HTTP headers</li>
 *     <li>fail-fast behavior when validation fails</li>
 * </ul>
 *
 * <p>Tests are grouped by responsibility using nested classes.
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
         * Verifies that a valid request produces a correctly populated {@link EventFlow}
         * and returns HTTP 201.
         */
        @Test
        void notifications_shouldCreateEventFlowAndReturnCreated_whenRequestIsValid() {
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
         * Verifies that the request is still accepted when subject is absent
         * but issuer remains valid.
         */
        @Test
        void notifications_shouldCreateEventFlowAndReturnCreated_whenSubjectIsNull() {
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
         * Verifies that blank request bodies are rejected before any pipeline execution.
         *
         * @param ignoredCaseName readable description of the invalid payload scenario
         * @param payload invalid request body under test
         */
        @ParameterizedTest(name = "{index} - {0}")
        @MethodSource("com.example.EstreemNotificationControllerAdapterTest#blankPayloadScenarios")
        void notifications_shouldRejectRequest_whenPayloadIsBlank(String ignoredCaseName, String payload) {
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
         * Verifies that invalid token combinations are rejected with the expected
         * business error code and message.
         *
         * @param ignoredCaseName readable description of the invalid token scenario
         * @param tokenContext token context under test
         * @param expectedMessage expected business error message
         */
        @ParameterizedTest(name = "{index} - {0}")
        @MethodSource("com.example.EstreemNotificationControllerAdapterTest#invalidTokenScenarios")
        void notifications_shouldRejectRequest_whenTokenIsInvalid(String ignoredCaseName,
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
     * Tests covering flow id extraction and validation.
     */
    @Nested
    class FlowIdValidation {

        /**
         * Verifies the current behavior when the flow_id header is missing entirely.
         *
         * <p>With the current implementation, accessing the missing header leads to a
         * {@link NullPointerException} before a business exception can be raised.
         */
        @Test
        void notifications_shouldFailWithNullPointerException_whenFlowIdHeaderIsMissing() {
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
         * Verifies that invalid flow_id header configurations are rejected.
         *
         * @param ignoredCaseName readable description of the invalid header scenario
         * @param headers HTTP headers under test
         * @param expectedMessage expected business error message
         */
        @ParameterizedTest(name = "{index} - {0}")
        @MethodSource("com.example.EstreemNotificationControllerAdapterTest#invalidFlowIdHeaderScenarios")
        void notifications_shouldRejectRequest_whenFlowIdHeaderIsInvalid(String ignoredCaseName,
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

    /**
     * Provides request body scenarios that must be rejected as blank payloads.
     *
     * @return stream of invalid blank payload cases
     */
    static Stream<Arguments> blankPayloadScenarios() {
        return Stream.of(
                Arguments.of("empty payload", ""),
                Arguments.of("whitespace-only payload", "   "),
                Arguments.of("newline-only payload", "\n"),
                Arguments.of("tab-only payload", "\t")
        );
    }

    /**
     * Provides token validation scenarios that must be rejected.
     *
     * @return stream of invalid token cases with expected messages
     */
    static Stream<Arguments> invalidTokenScenarios() {
        return Stream.of(
                Arguments.of(
                        "issuer is missing",
                        tokenContext(null, VALID_SUBJECT),
                        INVALID_TOKEN_MISSING_ISSUER_MESSAGE
                ),
                Arguments.of(
                        "issuer is shorter than allowed",
                        tokenContext("ab", VALID_SUBJECT),
                        INVALID_TOKEN_ISSUER_REGEX_MESSAGE
                ),
                Arguments.of(
                        "issuer contains forbidden characters",
                        tokenContext("ab@", VALID_SUBJECT),
                        INVALID_TOKEN_ISSUER_REGEX_MESSAGE
                ),
                Arguments.of(
                        "subject contains forbidden characters",
                        tokenContext(VALID_ISSUER, "##"),
                        INVALID_TOKEN_SUBJECT_REGEX_MESSAGE
                )
        );
    }

    /**
     * Provides invalid flow_id header scenarios.
     *
     * @return stream of invalid flow_id header cases with expected messages
     */
    static Stream<Arguments> invalidFlowIdHeaderScenarios() {
        HttpHeaders empty = new HttpHeaders();
        empty.put("flow_id", List.of());

        HttpHeaders multiple = new HttpHeaders();
        multiple.add("flow_id", "flow_123");
        multiple.add("flow_id", "flow_456");

        HttpHeaders nullValue = new HttpHeaders();
        nullValue.put("flow_id", Collections.singletonList(null));

        return Stream.of(
                Arguments.of("flow_id header is present but empty", empty, FLOW_ID_MISSING_MESSAGE),
                Arguments.of("more than one flow_id value is present", multiple, FLOW_ID_MULTIPLE_MESSAGE),
                Arguments.of("flow_id value is null", nullValue, FLOW_ID_INVALID_MESSAGE),
                Arguments.of("flow_id does not match the expected format", headersWithFlowId("bad"), FLOW_ID_INVALID_MESSAGE)
        );
    }

    /**
     * Executes the controller method while mocking token extraction from the request.
     *
     * @param request servlet request
     * @param body request body
     * @param headers HTTP headers
     * @param tokenContext token context returned by {@link JwtTokenUtil}
     * @return response returned by the controller
     */
    private ResponseEntity<Object> executeNotifications(HttpServletRequest request,
                                                        String body,
                                                        HttpHeaders headers,
                                                        TokenContext tokenContext) {
        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);
            return adapter.notifications(request, body, headers);
        }
    }

    /**
     * Creates a token context fixture for tests.
     *
     * @param issuer token issuer
     * @param subject token subject
     * @return token context instance
     */
    private static TokenContext tokenContext(String issuer, String subject) {
        return TokenContext.builder()
                .issuer(issuer)
                .subject(subject)
                .build();
    }

    /**
     * Creates HTTP headers containing a single flow_id value.
     *
     * @param flowId flow identifier
     * @return populated headers
     */
    private static HttpHeaders headersWithFlowId(String flowId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("flow_id", flowId);
        return headers;
    }
}
```

```
@Slf4j
@RequiredArgsConstructor
public class ApiFlowProcessorStrategyImpl implements FlowProcessorStrategy<ApiFlow> {

    private final ApplyTransformationPort applyTransformationPort;

    @Override
    public void doProcessFlow(ApiFlow flow,
                              ForwardFlowPort forwardFlowPort) {
        ApiFlowConfiguration apiFlowConfiguration = flow.getConfiguration();
        FlowDirection direction = flow.getFlowConfiguration().getDirection();
        checkIsAuthorized(flow, apiFlowConfiguration);

        ApiFlowConfigurationRequest apiFlowConfigurationRequest = apiFlowConfiguration.getRequestTransformations();
        if (apiFlowConfiguration.getRequestTransformations() != null) {
            ApiFlowRequestTransformationCtx apiFlowRequestTransformationCtx = ApiFlowRequestTransformationCtx.builder()
                    .apiFlowRequest(flow.getRequest())
                    .alias(apiFlowConfigurationRequest.getAlias())
                    .direction(direction)
                    .build();
            this.applyTransformationPort.applyTransformation(apiFlowRequestTransformationCtx);
        }

        forwardFlowPort.forwardFlow(flow);

        ApiFlowConfigurationResponse apiFlowConfigurationResponse = apiFlowConfiguration.getResponseTransformations();
        if (apiFlowConfigurationResponse != null) {
            ApiFlowResponseTransformationCtx apiFlowResponseTransformationCtx = ApiFlowResponseTransformationCtx.builder()
                    .apiFlowResponse(flow.getResponse())
                    .alias(apiFlowConfigurationResponse.getAlias())
                    .direction(direction)
                    .build();
            this.applyTransformationPort.applyTransformation(apiFlowResponseTransformationCtx);
        }
    }

    private void checkIsAuthorized(ApiFlow flow, ApiFlowConfiguration apiFlowConfiguration) {
        List<String> authorizedCodeAp = apiFlowConfiguration.getAuthorizedCodeAp();
        TokenContext tokenContext = flow.getRequest().getTokenContext();

        String issuer = tokenContext.issuer();
        String subject = tokenContext.subject();

        boolean authorizedForAll = authorizedCodeAp.contains("all");
        if (authorizedForAll) {
            return;
        }

        boolean authorizedIn = flow.getFlowDirection() == FlowDirection.IN
                && authorizedCodeAp.contains(issuer);
        if (authorizedIn) {
            return;
        }

        boolean authorizedOut = flow.getFlowDirection() == FlowDirection.OUT
                && (authorizedCodeAp.contains(issuer)
                && (subject == null || authorizedCodeAp.contains(subject)));
        if (authorizedOut) {
            return;
        }

        throw new GilCoreException(
                GilErrorCode.AUTHENTICATION_TOKEN_FAILED,
                "la liste de sources dans la configuration du flow et la source du flow (" + issuer + ", " + subject + ") ne correspondent pas."
        );
    }
}
```

```
/**
 * Unit tests for {@link ApiFlowProcessorStrategyImpl}.
 *
 * <p>This suite validates the orchestration responsibilities of the strategy:
 * <ul>
 *     <li>authorization checks before any processing</li>
 *     <li>request transformation triggering</li>
 *     <li>flow forwarding</li>
 *     <li>response transformation triggering</li>
 *     <li>execution order across these steps</li>
 * </ul>
 */
class ApiFlowProcessorStrategyImplTest {

    private ApplyTransformationPort applyTransformationPort;
    private ForwardFlowPort forwardFlowPort;
    private ApiFlowProcessorStrategyImpl strategy;

    @BeforeEach
    void setUp() {
        applyTransformationPort = mock(ApplyTransformationPort.class);
        forwardFlowPort = mock(ForwardFlowPort.class);
        strategy = new ApiFlowProcessorStrategyImpl(applyTransformationPort);
    }

    @Nested
    class AuthorizationFailures {

        /**
         * Verifies that unauthorized flows are rejected before any transformation
         * or forwarding is attempted.
         */
        @ParameterizedTest(name = "{index} - auth failure: {0}")
        @MethodSource("com.example.ApiFlowProcessorStrategyImplTest#unauthorizedFlowCases")
        void shouldThrowAuthenticationTokenFailed_whenFlowIsNotAuthorized(String ignoredCaseName,
                                                                          ApiFlow flow) {
            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, exception.getCode());
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }
    }

    @Nested
    class AuthorizationSuccessCases {

        /**
         * Verifies successful authorization scenarios for API flows.
         *
         * <p>Covered cases:
         * <ul>
         *     <li>"all" authorizes any flow</li>
         *     <li>IN flow authorized by issuer only</li>
         *     <li>IN flow authorized by issuer with null subject</li>
         *     <li>OUT flow authorized by issuer + subject</li>
         *     <li>OUT flow authorized by issuer with null subject</li>
         * </ul>
         */
        @ParameterizedTest(name = "{index} - {0}")
        @MethodSource("com.example.ApiFlowProcessorStrategyImplTest#authorizedFlowCases")
        void shouldProcessFlow_whenAuthorizationSucceeds(String ignoredCaseName, ApiFlow flow) {
            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }
    }

    @Nested
    class TransformationBehavior {

        @Test
        void shouldApplyRequestTransformationThenForwardThenApplyResponseTransformation() {
            ApiFlowConfigurationRequest requestTransformations = ApiFlowConfigurationRequest.builder()
                    .alias(List.of(AliasingTransformationConfiguration.builder().pointer("/req").build()))
                    .build();

            ApiFlowConfigurationResponse responseTransformations = ApiFlowConfigurationResponse.builder()
                    .alias(List.of(AliasingTransformationConfiguration.builder().pointer("/res").build()))
                    .build();

            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "sub123",
                    List.of("ap12345"),
                    requestTransformations,
                    responseTransformations
            );
            flow.setResponse(ApiFlowResponse.builder().build());

            strategy.doProcessFlow(flow, forwardFlowPort);

            InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);

            inOrder.verify(applyTransformationPort).applyTransformation(argThat(ctx ->
                    ctx instanceof ApiFlowRequestTransformationCtx requestCtx
                            && requestCtx.getApiFlowRequest().equals(flow.getRequest())
                            && requestCtx.getAlias().equals(requestTransformations.getAlias())
                            && requestCtx.getDirection() == FlowDirection.IN
            ));

            inOrder.verify(forwardFlowPort).forwardFlow(flow);

            inOrder.verify(applyTransformationPort).applyTransformation(argThat(ctx ->
                    ctx instanceof ApiFlowResponseTransformationCtx responseCtx
                            && responseCtx.getApiFlowResponse().equals(flow.getResponse())
                            && responseCtx.getAlias().equals(responseTransformations.getAlias())
                            && responseCtx.getDirection() == FlowDirection.IN
            ));
        }

        @Test
        void shouldApplyOnlyRequestTransformation_whenResponseTransformationIsNull() {
            ApiFlowConfigurationRequest requestTransformations = ApiFlowConfigurationRequest.builder()
                    .alias(List.of(AliasingTransformationConfiguration.builder().pointer("/req").build()))
                    .build();

            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "sub123",
                    List.of("ap12345"),
                    requestTransformations,
                    null
            );
            flow.setResponse(ApiFlowResponse.builder().build());

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(applyTransformationPort, times(1)).applyTransformation(argThat(ctx ->
                    ctx instanceof ApiFlowRequestTransformationCtx
            ));
            verify(forwardFlowPort).forwardFlow(flow);
        }

        @Test
        void shouldApplyOnlyResponseTransformation_whenRequestTransformationIsNull() {
            ApiFlowConfigurationResponse responseTransformations = ApiFlowConfigurationResponse.builder()
                    .alias(List.of(AliasingTransformationConfiguration.builder().pointer("/res").build()))
                    .build();

            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "sub123",
                    List.of("ap12345"),
                    null,
                    responseTransformations
            );
            flow.setResponse(ApiFlowResponse.builder().build());

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verify(applyTransformationPort, times(1)).applyTransformation(argThat(ctx ->
                    ctx instanceof ApiFlowResponseTransformationCtx
            ));
        }

        @Test
        void shouldOnlyForward_whenNoTransformationsAreConfigured() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "sub123",
                    List.of("ap12345"),
                    null,
                    null
            );
            flow.setResponse(ApiFlowResponse.builder().build());

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }
    }

    static Stream<Arguments> unauthorizedFlowCases() {
        return Stream.of(
                Arguments.of(
                        "IN flow with unauthorized issuer",
                        flow(
                                FlowDirection.IN,
                                "ap99999",
                                "sub123",
                                List.of("ap12345", "ap67890"),
                                null,
                                null
                        )
                ),
                Arguments.of(
                        "OUT flow with unauthorized issuer",
                        flow(
                                FlowDirection.OUT,
                                "ap99999",
                                "partner01",
                                List.of("ap12345", "partner01"),
                                null,
                                null
                        )
                ),
                Arguments.of(
                        "OUT flow with authorized issuer but unauthorized subject",
                        flow(
                                FlowDirection.OUT,
                                "ap12345",
                                "partner99",
                                List.of("ap12345", "partner01", "partner02"),
                                null,
                                null
                        )
                )
        );
    }

    /**
     * Provides authorized API flow scenarios.
     *
     * @return successful authorization cases for both IN and OUT directions
     */
    static Stream<Arguments> authorizedFlowCases() {
        return Stream.of(
                Arguments.of(
                        "authorized for all",
                        flow(
                                FlowDirection.IN,
                                "unknownIssuer",
                                "unknownSubject",
                                List.of("all"),
                                null,
                                null
                        )
                ),
                Arguments.of(
                        "IN flow with authorized issuer",
                        flow(
                                FlowDirection.IN,
                                "ap12345",
                                "sub999",
                                List.of("ap12345"),
                                null,
                                null
                        )
                ),
                Arguments.of(
                        "IN flow with authorized issuer and null subject",
                        flow(
                                FlowDirection.IN,
                                "ap12345",
                                null,
                                List.of("ap12345"),
                                null,
                                null
                        )
                ),
                Arguments.of(
                        "OUT flow with authorized issuer and subject",
                        flow(
                                FlowDirection.OUT,
                                "ap12345",
                                "partner01",
                                List.of("ap12345", "partner01", "partner02"),
                                null,
                                null
                        )
                ),
                Arguments.of(
                        "OUT flow with authorized issuer and null subject",
                        flow(
                                FlowDirection.OUT,
                                "ap12345",
                                null,
                                List.of("ap12345"),
                                null,
                                null
                        )
                )
        );
    }

    private static ApiFlow flow(FlowDirection direction,
                                String issuer,
                                String subject,
                                List<String> authorizedCodeAp,
                                ApiFlowConfigurationRequest requestTransformations,
                                ApiFlowConfigurationResponse responseTransformations) {

        TokenContext tokenContext = TokenContext.builder()
                .issuer(issuer)
                .subject(subject)
                .build();

        ApiFlowRequest request = ApiFlowRequest.builder()
                .tokenContext(tokenContext)
                .build();

        ApiFlowConfiguration configuration = ApiFlowConfiguration.builder()
                .flowId("flow123")
                .direction(direction)
                .authorizedCodeAp(authorizedCodeAp)
                .requestTransformations(requestTransformations)
                .responseTransformations(responseTransformations)
                .build();

        return ApiFlow.builder()
                .flowId("flow123")
                .flowDirection(direction)
                .configuration(configuration)
                .request(request)
                .build();
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





