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



