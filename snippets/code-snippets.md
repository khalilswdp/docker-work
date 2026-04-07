```

class EstreemApiGatewayAdapterTest {

    private FromPartnerToBnppUseCase fromPartnerToBnppUseCase;
    private EstreemApiGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        fromPartnerToBnppUseCase = mock(FromPartnerToBnppUseCase.class);
        adapter = new EstreemApiGatewayAdapter(fromPartnerToBnppUseCase);
    }

    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid_andSubjectIsPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject("sub_123")
                .build();

        mockSuccessfulPipeline(200, "{\"ok\":true}");

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid_andSubjectIsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn(null);

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject(null)
                .build();

        mockSuccessfulPipeline(200, "{\"ok\":true}");

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    @Test
    void adapter_shouldSendEmptyRequestPayload_whenBodyIsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject("sub123")
                .build();

        doAnswer(invocation -> {
            ApiFlow apiFlow = invocation.getArgument(0);

            assertEquals("", apiFlow.getRequest().getRequestPayload());

            apiFlow.setResponse(
                    ApiFlowResponse.builder()
                            .statusCode(200)
                            .responseHeaders(Map.of("Content-Type", List.of("application/json")))
                            .responsePayload("{\"ok\":true}")
                            .build()
            );
            return null;
        }).when(fromPartnerToBnppUseCase).doPipeline(any(ApiFlow.class));

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, null, headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    @Test
    void adapter_shouldReturnEmptyResponseBody_whenPipelineResponsePayloadIsEmpty() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject("sub123")
                .build();

        mockSuccessfulPipeline(204, null);

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(204, response.getStatusCode().value());
            assertEquals("", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("invalidTokenContexts")
    void adapter_shouldThrowException_whenTokenIsInvalid(String ignoredCaseName, TokenContext tokenContext) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> adapter.adapter("flow123", request, "body", headers)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            verify(fromPartnerToBnppUseCase, never()).doPipeline(any());
        }
    }

    private static Stream<Arguments> invalidTokenContexts() {
        return Stream.of(
                Arguments.of(
                        "issuer is null",
                        TokenContext.builder()
                                .issuer(null)
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "issuer is invalid because too short",
                        TokenContext.builder()
                                .issuer("ab")
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "issuer is invalid because too long",
                        TokenContext.builder()
                                .issuer("abcdefghijklmnop")
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "issuer is invalid because contains forbidden character",
                        TokenContext.builder()
                                .issuer("ap@123")
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "subject is invalid because too short",
                        TokenContext.builder()
                                .issuer("ap12345")
                                .subject("ab")
                                .build()
                ),
                Arguments.of(
                        "subject is invalid because too long",
                        TokenContext.builder()
                                .issuer("ap12345")
                                .subject("abcdefghijklmnop")
                                .build()
                ),
                Arguments.of(
                        "subject is invalid because contains forbidden character",
                        TokenContext.builder()
                                .issuer("ap12345")
                                .subject("sub#123")
                                .build()
                )
        );
    }

    private void mockSuccessfulPipeline(int statusCode, String responsePayload) {
        doAnswer(invocation -> {
            ApiFlow apiFlow = invocation.getArgument(0);
            apiFlow.setResponse(
                    ApiFlowResponse.builder()
                            .statusCode(statusCode)
                            .responseHeaders(Map.of("Content-Type", List.of("application/json")))
                            .responsePayload(responsePayload)
                            .build()
            );
            return null;
        }).when(fromPartnerToBnppUseCase).doPipeline(any(ApiFlow.class));
    }
}

```
```

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

        @ParameterizedTest
        @MethodSource("com.example.ApiFlowProcessorStrategyImplTest#malformedAuthorizedCodeApCases")
        void shouldThrowCoreConfigMalformed_whenAuthorizedCodeApIsMissing(List<String> authorizedCodeAp) {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "sub123",
                    authorizedCodeAp,
                    null,
                    null
            );

            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.CORE_CONFIG_MALFORMED, exception.getCode());
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }

        @Test
        void shouldThrowAuthenticationTokenFailed_whenInFlowIssuerIsNotAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap99999",
                    "sub123",
                    List.of("ap12345", "ap67890"),
                    null,
                    null
            );

            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, exception.getCode());
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }

        @Test
        void shouldThrowAuthenticationTokenFailed_whenOutFlowIssuerIsNotApigee() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "ap12345",
                    "partner01",
                    List.of("partner01"),
                    null,
                    null
            );

            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, exception.getCode());
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }

        @Test
        void shouldThrowAuthenticationTokenFailed_whenOutFlowSubjectIsNotAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "apigee",
                    "partner99",
                    List.of("partner01", "partner02"),
                    null,
                    null
            );

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

        @Test
        void shouldProcessFlow_whenAuthorizedForAll() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "unknownIssuer",
                    "unknownSubject",
                    List.of("all"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        @Test
        void shouldProcessFlow_whenInFlowIssuerIsAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "ignoredSubject",
                    List.of("ap12345", "ap67890"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        @Test
        void shouldProcessFlow_whenOutFlowIssuerIsApigeeAndSubjectIsAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "apigee",
                    "partner01",
                    List.of("partner01", "partner02"),
                    null,
                    null
            );

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

    static Stream<Arguments> malformedAuthorizedCodeApCases() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(List.of())
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
 *
 * <p>The tests are grouped by behavior using nested classes so that authorization
 * and transformation concerns remain easy to locate and evolve independently.
 */
class ApiFlowProcessorStrategyImplTest {

    private ApplyTransformationPort applyTransformationPort;
    private ForwardFlowPort forwardFlowPort;
    private ApiFlowProcessorStrategyImpl strategy;

    /**
     * Creates a new strategy instance and mocks its collaborators before each test.
     */
    @BeforeEach
    void setUp() {
        applyTransformationPort = mock(ApplyTransformationPort.class);
        forwardFlowPort = mock(ForwardFlowPort.class);
        strategy = new ApiFlowProcessorStrategyImpl(applyTransformationPort);
    }

    /**
     * Tests for authorization failures.
     *
     * <p>These scenarios verify that processing stops immediately when the flow
     * is not authorized or when the configuration is malformed.
     */
    @Nested
    class AuthorizationFailures {

        /**
         * Verifies that a missing or empty authorized source list is treated as
         * malformed configuration.
         *
         * @param authorizedCodeAp authorized sources configured for the flow
         */
        @ParameterizedTest
        @MethodSource("com.example.ApiFlowProcessorStrategyImplTest#malformedAuthorizedCodeApCases")
        void shouldThrowCoreConfigMalformed_whenAuthorizedCodeApIsMissing(List<String> authorizedCodeAp) {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "sub123",
                    authorizedCodeAp,
                    null,
                    null
            );

            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.CORE_CONFIG_MALFORMED, exception.getCode());

            // Authorization failure must stop the process immediately.
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }

        /**
         * Verifies that an IN flow is rejected when the issuer is not included
         * in the configured authorized source list.
         */
        @Test
        void shouldThrowAuthenticationTokenFailed_whenInFlowIssuerIsNotAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap99999",
                    "sub123",
                    List.of("ap12345", "ap67890"),
                    null,
                    null
            );

            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, exception.getCode());
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }

        /**
         * Verifies that an OUT flow is rejected when the issuer is not "apigee",
         * even if the subject is otherwise present in the authorized list.
         */
        @Test
        void shouldThrowAuthenticationTokenFailed_whenOutFlowIssuerIsNotApigee() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "ap12345",
                    "partner01",
                    List.of("partner01"),
                    null,
                    null
            );

            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, exception.getCode());
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }

        /**
         * Verifies that an OUT flow is rejected when the issuer is "apigee"
         * but the subject is not part of the authorized source list.
         */
        @Test
        void shouldThrowAuthenticationTokenFailed_whenOutFlowSubjectIsNotAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "apigee",
                    "partner99",
                    List.of("partner01", "partner02"),
                    null,
                    null
            );

            GilCoreException exception = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, exception.getCode());
            verifyNoInteractions(applyTransformationPort);
            verifyNoInteractions(forwardFlowPort);
        }
    }

    /**
     * Tests for successful authorization paths.
     *
     * <p>These scenarios verify that, once authorization passes, the strategy
     * proceeds to forward the flow.
     */
    @Nested
    class AuthorizationSuccessCases {

        /**
         * Verifies that the special source "all" authorizes the flow regardless
         * of issuer and subject values.
         */
        @Test
        void shouldProcessFlow_whenAuthorizedForAll() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "unknownIssuer",
                    "unknownSubject",
                    List.of("all"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * Verifies that an IN flow is authorized when the token issuer is explicitly
         * present in the configured authorized source list.
         */
        @Test
        void shouldProcessFlow_whenInFlowIssuerIsAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    "ignoredSubject",
                    List.of("ap12345", "ap67890"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * Verifies that an OUT flow is authorized only when:
         * <ul>
         *     <li>issuer is "apigee"</li>
         *     <li>subject is present in the configured authorized source list</li>
         * </ul>
         */
        @Test
        void shouldProcessFlow_whenOutFlowIssuerIsApigeeAndSubjectIsAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "apigee",
                    "partner01",
                    List.of("partner01", "partner02"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }
    }

    /**
     * Tests focused on request/response transformation behavior and execution order.
     */
    @Nested
    class TransformationBehavior {

        /**
         * Verifies the full happy path:
         * <ol>
         *     <li>request transformation is applied</li>
         *     <li>flow is forwarded</li>
         *     <li>response transformation is applied</li>
         * </ol>
         *
         * <p>The test also verifies the content of the transformation contexts.
         */
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

            // Request transformation must happen before the flow is forwarded.
            inOrder.verify(applyTransformationPort).applyTransformation(argThat(ctx ->
                    ctx instanceof ApiFlowRequestTransformationCtx requestCtx
                            && requestCtx.getApiFlowRequest().equals(flow.getRequest())
                            && requestCtx.getAlias().equals(requestTransformations.getAlias())
                            && requestCtx.getDirection() == FlowDirection.IN
            ));

            inOrder.verify(forwardFlowPort).forwardFlow(flow);

            // Response transformation must happen only after the downstream call.
            inOrder.verify(applyTransformationPort).applyTransformation(argThat(ctx ->
                    ctx instanceof ApiFlowResponseTransformationCtx responseCtx
                            && responseCtx.getApiFlowResponse().equals(flow.getResponse())
                            && responseCtx.getAlias().equals(responseTransformations.getAlias())
                            && responseCtx.getDirection() == FlowDirection.IN
            ));
        }

        /**
         * Verifies that only the request transformation is applied when no response
         * transformation is configured.
         */
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

        /**
         * Verifies that only the response transformation is applied when no request
         * transformation is configured.
         */
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

        /**
         * Verifies that the strategy only forwards the flow when no request
         * or response transformations are configured.
         */
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

    /**
     * Provides malformed configuration inputs for authorization validation.
     *
     * @return cases where {@code authorizedCodeAp} is missing or empty
     */
    static Stream<Arguments> malformedAuthorizedCodeApCases() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(List.of())
        );
    }

    /**
     * Creates a reusable {@link ApiFlow} fixture for strategy tests.
     *
     * <p>This helper builds a coherent flow graph containing:
     * <ul>
     *     <li>a token context</li>
     *     <li>a request carrying that token context</li>
     *     <li>a flow configuration with direction, authorized sources, and optional transformations</li>
     * </ul>
     *
     * @param direction flow direction under test
     * @param issuer token issuer
     * @param subject token subject
     * @param authorizedCodeAp configured authorized sources
     * @param requestTransformations optional request transformation config
     * @param responseTransformations optional response transformation config
     * @return fully initialized flow fixture
     */
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


```
/**
 * Unit tests for {@link EstreemApiGatewayAdapter}.
 *
 * <p>This test suite validates the observable behavior of the adapter:
 * <ul>
 *     <li>accepting valid token contexts</li>
 *     <li>rejecting invalid token contexts</li>
 *     <li>mapping null request payloads to empty strings</li>
 *     <li>mapping null response payloads to empty strings</li>
 *     <li>delegating valid flows to the downstream use case</li>
 * </ul>
 *
 * <p>The goal is to verify the adapter contract, not the internal implementation
 * details of downstream services.
 */
class EstreemApiGatewayAdapterTest {

    private FromPartnerToBnppUseCase fromPartnerToBnppUseCase;
    private EstreemApiGatewayAdapter adapter;

    /**
     * Initializes the adapter under test with a mocked downstream use case.
     */
    @BeforeEach
    void setUp() {
        fromPartnerToBnppUseCase = mock(FromPartnerToBnppUseCase.class);
        adapter = new EstreemApiGatewayAdapter(fromPartnerToBnppUseCase);
    }

    /**
     * Verifies that the adapter accepts a token when:
     * <ul>
     *     <li>ISS is valid</li>
     *     <li>SUB is present and valid</li>
     * </ul>
     *
     * <p>Also verifies that the adapter delegates the flow to the pipeline
     * and returns the pipeline response as-is.
     */
    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid_andSubjectIsPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject("sub_123")
                .build();

        // Simulate a successful pipeline execution and a valid downstream response.
        mockSuccessfulPipeline(200, "{\"ok\":true}");

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    /**
     * Verifies that the adapter accepts a token when:
     * <ul>
     *     <li>ISS is valid</li>
     *     <li>SUB is null</li>
     * </ul>
     *
     * <p>This documents the current validation rule that SUB is optional.
     */
    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid_andSubjectIsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn(null);

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject(null)
                .build();

        mockSuccessfulPipeline(200, "{\"ok\":true}");

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    /**
     * Verifies that a null request body is normalized by the adapter into an
     * empty payload before the flow is sent to the pipeline.
     */
    @Test
    void adapter_shouldSendEmptyRequestPayload_whenBodyIsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject("sub123")
                .build();

        doAnswer(invocation -> {
            ApiFlow apiFlow = invocation.getArgument(0);

            // The adapter should normalize a null body to an empty String.
            assertEquals("", apiFlow.getRequest().getRequestPayload());

            apiFlow.setResponse(
                    ApiFlowResponse.builder()
                            .statusCode(200)
                            .responseHeaders(Map.of("Content-Type", List.of("application/json")))
                            .responsePayload("{\"ok\":true}")
                            .build()
            );
            return null;
        }).when(fromPartnerToBnppUseCase).doPipeline(any(ApiFlow.class));

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, null, headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    /**
     * Verifies that a null downstream response payload is normalized by the adapter
     * into an empty response body.
     */
    @Test
    void adapter_shouldReturnEmptyResponseBody_whenPipelineResponsePayloadIsEmpty() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("ap12345")
                .subject("sub123")
                .build();

        // Simulate a downstream response with no payload.
        mockSuccessfulPipeline(204, null);

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(204, response.getStatusCode().value());
            assertEquals("", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    /**
     * Verifies that invalid token contexts are rejected before the pipeline is invoked.
     *
     * @param ignoredCaseName readable label for the parameterized scenario
     * @param tokenContext token context under test
     */
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("invalidTokenContexts")
    void adapter_shouldThrowException_whenTokenIsInvalid(String ignoredCaseName, TokenContext tokenContext) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> adapter.adapter("flow123", request, "body", headers)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            verify(fromPartnerToBnppUseCase, never()).doPipeline(any());
        }
    }

    /**
     * Provides invalid token context scenarios for parameterized validation tests.
     *
     * <p>The invalid cases cover:
     * <ul>
     *     <li>missing issuer</li>
     *     <li>issuer too short</li>
     *     <li>issuer too long</li>
     *     <li>issuer containing forbidden characters</li>
     *     <li>subject too short</li>
     *     <li>subject too long</li>
     *     <li>subject containing forbidden characters</li>
     * </ul>
     *
     * @return stream of invalid token scenarios
     */
    private static Stream<Arguments> invalidTokenContexts() {
        return Stream.of(
                Arguments.of(
                        "issuer is null",
                        TokenContext.builder()
                                .issuer(null)
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "issuer is invalid because too short",
                        TokenContext.builder()
                                .issuer("ab")
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "issuer is invalid because too long",
                        TokenContext.builder()
                                .issuer("abcdefghijklmnop")
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "issuer is invalid because contains forbidden character",
                        TokenContext.builder()
                                .issuer("ap@123")
                                .subject("sub123")
                                .build()
                ),
                Arguments.of(
                        "subject is invalid because too short",
                        TokenContext.builder()
                                .issuer("ap12345")
                                .subject("ab")
                                .build()
                ),
                Arguments.of(
                        "subject is invalid because too long",
                        TokenContext.builder()
                                .issuer("ap12345")
                                .subject("abcdefghijklmnop")
                                .build()
                ),
                Arguments.of(
                        "subject is invalid because contains forbidden character",
                        TokenContext.builder()
                                .issuer("ap12345")
                                .subject("sub#123")
                                .build()
                )
        );
    }

    /**
     * Stubs the downstream use case so that it mutates the {@link ApiFlow}
     * with a successful response.
     *
     * @param statusCode simulated HTTP status returned by the pipeline
     * @param responsePayload simulated downstream payload
     */
    private void mockSuccessfulPipeline(int statusCode, String responsePayload) {
        doAnswer(invocation -> {
            ApiFlow apiFlow = invocation.getArgument(0);
            apiFlow.setResponse(
                    ApiFlowResponse.builder()
                            .statusCode(statusCode)
                            .responseHeaders(Map.of("Content-Type", List.of("application/json")))
                            .responsePayload(responsePayload)
                            .build()
            );
            return null;
        }).when(fromPartnerToBnppUseCase).doPipeline(any(ApiFlow.class));
    }
}
```

```
private void checkIsAuthorized(ApiFlow flow, ApiFlowConfiguration apiFlowConfiguration) {

        List<String> authorizedCodeAp = apiFlowConfiguration.getAuthorizedCodeAp();
        TokenContext tokenContext = flow.getRequest().getTokenContext();

        String issuer = tokenContext.issuer();
        String subject = tokenContext.subject();

        if (authorizedCodeAp == null || authorizedCodeAp.isEmpty()) {
            throw new GilCoreException(GilErrorCode.CORE_CONFIG_MALFORMED,
                    "Aucune liste authorizedCodeAp n'est définie dans la configuration du flow");
        }

        boolean authorizedForAll = authorizedCodeAp.contains("all");
        if (authorizedForAll) {
            return;
        }

        boolean authorizedIn = flow.getFlowDirection() == FlowDirection.IN && authorizedCodeAp.contains(issuer);
        if (authorizedIn) {
            return;
        }

        boolean authorizedOut = flow.getFlowDirection() == FlowDirection.OUT && issuer.equals("apigee") && authorizedCodeAp.contains(subject);
        if (authorizedOut) {
            return;
        }

        throw new GilCoreException(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, "la liste de sources dans la configuration du flow et la source du flow (" + issuer + ", " + subject + ") ne correspondent pas.");

    }
```

```
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





