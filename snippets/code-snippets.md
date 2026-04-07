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

        throw new GilCoreException(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, "la liste de sources dans la configuration du flow et la source du flow (" + issuer + ", " + subject + ") ne correspondent pas.");

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
        void shouldThrowAuthenticationTokenFailed_whenOutFlowIssuerIsNotAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "ap99999",
                    "partner01",
                    List.of("ap12345", "partner01"),
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
        void shouldThrowAuthenticationTokenFailed_whenOutFlowIssuerIsAuthorizedButSubjectIsNotAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "ap12345",
                    "partner99",
                    List.of("ap12345", "partner01", "partner02"),
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
                    "sub999",
                    List.of("ap12345"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        @Test
        void shouldProcessFlow_whenInFlowIssuerIsAuthorizedAndSubjectIsNull() {
            ApiFlow flow = flow(
                    FlowDirection.IN,
                    "ap12345",
                    null,
                    List.of("ap12345"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        @Test
        void shouldProcessFlow_whenOutFlowIssuerAndSubjectAreAuthorized() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "ap12345",
                    "partner01",
                    List.of("ap12345", "partner01", "partner02"),
                    null,
                    null
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        @Test
        void shouldProcessFlow_whenOutFlowIssuerIsAuthorizedAndSubjectIsNull() {
            ApiFlow flow = flow(
                    FlowDirection.OUT,
                    "ap12345",
                    null,
                    List.of("ap12345"),
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





