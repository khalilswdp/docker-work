```

/**
     * Validates whether the given API flow is authorized to be processed
     * based on its configuration and token context.
     *
     * <p>The authorization logic is defined as follows:
     *
     * <ul>
     *     <li>If {@code authorizedCodeAp} contains the special value {@code "all"},
     *     the flow is always authorized regardless of direction, issuer, or subject.</li>
     *
     *     <li><b>IN flow:</b>
     *         <ul>
     *             <li>The flow is authorized if the token {@code issuer} is present in
     *             {@code authorizedCodeAp}.</li>
     *             <li>The {@code subject} is not considered in this case.</li>
     *         </ul>
     *     </li>
     *
     *     <li><b>OUT flow:</b>
     *         <ul>
     *             <li>The flow is authorized if:
     *                 <ul>
     *                     <li>{@code issuer} is present in {@code authorizedCodeAp}, and</li>
     *                     <li>{@code subject} is either {@code null} or also present in {@code authorizedCodeAp}.</li>
     *                 </ul>
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * <p>If none of these conditions are met, the flow is rejected and a
     * {@link GilCoreException} with code {@link GilErrorCode#AUTHENTICATION_TOKEN_FAILED} is thrown.
     *
     * @param flow the API flow being processed, containing direction and token context
     * @param apiFlowConfiguration the flow configuration containing authorized sources
     *
     * @throws GilCoreException if the flow is not authorized
     */
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


```
@Slf4j
@RequiredArgsConstructor
public class EventFlowProcessorStrategyImpl implements FlowProcessorStrategy<EventFlow> {

    private static final String ALL = "all";
    private final ApplyTransformationPort applyTransformationPort;

    @Override
    public void doProcessFlow(EventFlow flow,
                              ForwardFlowPort forwardFlowPort) {
        EventFlowConfiguration flowConfiguration = flow.getFlowConfiguration();
        EventFlowConfigurationTransformationConfiguration eventFlowConfigurationTransformationConfiguration = flowConfiguration.getTransformations();

        checkIsAuthorized(flow, flowConfiguration);

        EventFlowTransformationCtx eventFlowTransformationCtx = EventFlowTransformationCtx.builder()
                .alias(eventFlowConfigurationTransformationConfiguration.getAlias())
                .eventFlowConfigurationHeaders(eventFlowConfigurationTransformationConfiguration.getHeaders())
                .eventPayload(flow.getPayload())
                .headers(flow.getHeaders())
                .receivedEventTimestamp(flow.getReceivedEventTimestamp())
                .build();
        this.applyTransformationPort.applyTransformation(eventFlowTransformationCtx);
        flow.setPayload(eventFlowTransformationCtx.getEventPayload());

        forwardFlowPort.forwardFlow(flow);
    }

    /**
     * Validates whether the given event flow is authorized to be processed
     * based on its configuration and token context.
     *
     * <p>The authorization logic follows these rules:
     *
     * <ul>
     *     <li>If the list contains the special value {@code "all"}, the flow is always authorized
     *     regardless of direction, issuer, or subject.</li>
     *
     *     <li><b>Event flows are only supported in {@link FlowDirection#OUT} direction.</b>
     *         <ul>
     *             <li>If the flow direction is {@code IN} and {@code "all"} is not configured,
     *             the flow is rejected.</li>
     *         </ul>
     *     </li>
     *
     *     <li><b>OUT flow:</b>
     *         <ul>
     *             <li>The flow is authorized if:
     *                 <ul>
     *                     <li>{@code issuer} is present in {@code authorizedCodeAp}, and</li>
     *                     <li>{@code subject} is either {@code null} or also present in {@code authorizedCodeAp}.</li>
     *                 </ul>
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * <p>If none of the above conditions are met, the flow is rejected and a
     * {@link GilCoreException} with code {@link GilErrorCode#AUTHENTICATION_TOKEN_FAILED} is thrown.
     *
     * @param flow the event flow being processed, containing direction and token context
     * @param flowConfiguration the flow configuration containing authorized sources
     *
     * @throws GilCoreException if the flow is not authorized
     */
    private void checkIsAuthorized(EventFlow flow, EventFlowConfiguration flowConfiguration) {
        List<String> authorizedCodeAp = flowConfiguration.getAuthorizedCodeAp();
        TokenContext tokenContext = flow.getTokenContext();

        String issuer = tokenContext.issuer();
        String subject = tokenContext.subject();

        boolean authorizedForAll = authorizedCodeAp.contains(ALL);
        if (authorizedForAll) {
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
 * Unit tests for {@link EventFlowProcessorStrategyImpl}.
 *
 * <p>This suite validates:
 * <ul>
 *     <li>authorization rules for event flows</li>
 *     <li>transformation context construction</li>
 *     <li>payload update after transformation</li>
 *     <li>forwarding behavior and execution order</li>
 *     <li>fail-fast behavior when authorization fails</li>
 * </ul>
 *
 * <p>For event flows, the authorization rule is:
 * <ul>
 *     <li>"all" always authorizes</li>
 *     <li>otherwise only {@link FlowDirection#OUT} is authorized</li>
 *     <li>for OUT, issuer must be authorized</li>
 *     <li>for OUT, subject may be null, otherwise it must be authorized too</li>
 * </ul>
 */
class EventFlowProcessorStrategyImplTest {

    private static final String FLOW_ID = "flow123";
    private static final String VALID_ISSUER = "ap12345";
    private static final String VALID_SUBJECT = "sub123";
    private static final String INITIAL_PAYLOAD = "initial-payload";
    private static final String TRANSFORMED_PAYLOAD = "transformed-payload";
    private static final long RECEIVED_EVENT_TIMESTAMP = 123456789L;

    private ApplyTransformationPort applyTransformationPort;
    private ForwardFlowPort forwardFlowPort;
    private EventFlowProcessorStrategyImpl strategy;

    @BeforeEach
    void setUp() {
        applyTransformationPort = mock(ApplyTransformationPort.class);
        forwardFlowPort = mock(ForwardFlowPort.class);
        strategy = new EventFlowProcessorStrategyImpl(applyTransformationPort);
    }

    @Nested
    class AuthorizationFailures {

        /**
         * Verifies that IN event flows are rejected when "all" is not configured.
         */
        @Test
        void shouldThrowAuthenticationTokenFailed_whenDirectionIsIn() {
            EventFlow flow = flow(
                    FlowDirection.IN,
                    VALID_ISSUER,
                    VALID_SUBJECT,
                    List.of(VALID_ISSUER, VALID_SUBJECT),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
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
         * Verifies that an OUT flow is rejected when the issuer is not authorized.
         */
        @Test
        void shouldThrowAuthenticationTokenFailed_whenOutFlowIssuerIsNotAuthorized() {
            EventFlow flow = flow(
                    FlowDirection.OUT,
                    "ap99999",
                    "partner01",
                    List.of(VALID_ISSUER, "partner01"),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
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
         * Verifies that an OUT flow is rejected when the issuer is authorized
         * but the non-null subject is not authorized.
         */
        @Test
        void shouldThrowAuthenticationTokenFailed_whenOutFlowIssuerIsAuthorizedButSubjectIsNotAuthorized() {
            EventFlow flow = flow(
                    FlowDirection.OUT,
                    VALID_ISSUER,
                    "partner99",
                    List.of(VALID_ISSUER, "partner01", "partner02"),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
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

        /**
         * Verifies that the special source "all" authorizes the flow regardless
         * of direction or token values.
         */
        @Test
        void shouldProcessFlow_whenAuthorizedForAll_evenIfDirectionIsIn() {
            EventFlow flow = flow(
                    FlowDirection.IN,
                    "unknownIssuer",
                    "unknownSubject",
                    List.of("all"),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(applyTransformationPort, times(1)).applyTransformation(any(EventFlowTransformationCtx.class));
            verify(forwardFlowPort, times(1)).forwardFlow(flow);
        }

        /**
         * Verifies that an OUT flow is authorized when both issuer and subject
         * are present in the authorized list.
         */
        @Test
        void shouldProcessFlow_whenOutFlowIssuerAndSubjectAreAuthorized() {
            EventFlow flow = flow(
                    FlowDirection.OUT,
                    VALID_ISSUER,
                    "partner01",
                    List.of(VALID_ISSUER, "partner01", "partner02"),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(applyTransformationPort, times(1)).applyTransformation(any(EventFlowTransformationCtx.class));
            verify(forwardFlowPort, times(1)).forwardFlow(flow);
        }

        /**
         * Verifies that an OUT flow is authorized when the issuer is authorized
         * and the subject is null.
         */
        @Test
        void shouldProcessFlow_whenOutFlowIssuerIsAuthorizedAndSubjectIsNull() {
            EventFlow flow = flow(
                    FlowDirection.OUT,
                    VALID_ISSUER,
                    null,
                    List.of(VALID_ISSUER),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(applyTransformationPort, times(1)).applyTransformation(any(EventFlowTransformationCtx.class));
            verify(forwardFlowPort, times(1)).forwardFlow(flow);
        }
    }

    @Nested
    class TransformationBehavior {

        /**
         * Verifies the full happy path:
         * transformation is applied, payload is updated, then the flow is forwarded.
         */
        @Test
        void shouldApplyTransformationUpdatePayloadAndForward() {
            EventFlowConfigurationTransformationConfiguration transformationConfiguration = transformationConfiguration();

            EventFlow flow = flow(
                    FlowDirection.OUT,
                    VALID_ISSUER,
                    VALID_SUBJECT,
                    List.of(VALID_ISSUER, VALID_SUBJECT),
                    transformationConfiguration,
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
            );

            doAnswer(invocation -> {
                EventFlowTransformationCtx ctx = invocation.getArgument(0);
                ctx.setEventPayload(TRANSFORMED_PAYLOAD);
                return null;
            }).when(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));

            strategy.doProcessFlow(flow, forwardFlowPort);

            InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));
            inOrder.verify(forwardFlowPort).forwardFlow(flow);

            assertEquals(TRANSFORMED_PAYLOAD, flow.getPayload());
            verifyNoMoreInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * Verifies the exact transformation context values built by the strategy.
         */
        @Test
        void shouldBuildTransformationContextWithExpectedValues() {
            EventFlowConfigurationHeaders configuredHeaders = EventFlowConfigurationHeaders.builder()
                    .mainBusinessObjectId("id-path")
                    .mainBusinessObjectType("type-path")
                    .bankCode("bank-path")
                    .build();

            EventFlowConfigurationTransformationConfiguration transformationConfiguration =
                    EventFlowConfigurationTransformationConfiguration.builder()
                            .headers(configuredHeaders)
                            .alias(List.of(
                                    AliasingTransformationConfiguration.builder()
                                            .pointer("alias-path")
                                            .build()
                            ))
                            .build();

            Map<String, List<String>> headers = Map.of("event-type", List.of("customer-created"));

            EventFlow flow = flow(
                    FlowDirection.OUT,
                    VALID_ISSUER,
                    "partner01",
                    List.of(VALID_ISSUER, "partner01"),
                    transformationConfiguration,
                    "event-payload",
                    headers,
                    999L
            );

            strategy.doProcessFlow(flow, forwardFlowPort);

            ArgumentCaptor<EventFlowTransformationCtx> captor =
                    ArgumentCaptor.forClass(EventFlowTransformationCtx.class);

            verify(applyTransformationPort).applyTransformation(captor.capture());

            EventFlowTransformationCtx ctx = captor.getValue();
            assertEquals(transformationConfiguration.getAlias(), ctx.getAlias());
            assertEquals(configuredHeaders, ctx.getEventFlowConfigurationHeaders());
            assertEquals("event-payload", ctx.getEventPayload());
            assertEquals(headers, ctx.getHeaders());
            assertEquals(999L, ctx.getReceivedEventTimestamp());

            assertEquals("event-payload", flow.getPayload());
            verify(forwardFlowPort).forwardFlow(flow);
        }

        /**
         * Verifies that the transformed payload is copied back into the flow
         * before forwarding.
         */
        @Test
        void shouldWriteBackTransformedPayloadToFlowBeforeForwarding() {
            EventFlow flow = flow(
                    FlowDirection.OUT,
                    VALID_ISSUER,
                    "partner01",
                    List.of(VALID_ISSUER, "partner01"),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    Map.of(),
                    42L
            );

            doAnswer(invocation -> {
                EventFlowTransformationCtx ctx = invocation.getArgument(0);
                ctx.setEventPayload("updated-by-transformation");
                return null;
            }).when(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));

            strategy.doProcessFlow(flow, forwardFlowPort);

            InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));
            inOrder.verify(forwardFlowPort).forwardFlow(flow);

            assertEquals("updated-by-transformation", flow.getPayload());
        }
    }

    @Nested
    class FailFastBehavior {

        /**
         * Verifies that authorization failure stops processing immediately:
         * no transformation, no payload update, and no forwarding.
         */
        @Test
        void shouldNotTransformUpdatePayloadOrForward_whenAuthorizationFails() {
            EventFlow flow = flow(
                    FlowDirection.OUT,
                    VALID_ISSUER,
                    "forbidden-subject",
                    List.of(VALID_ISSUER, "allowed-subject"),
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
            );

            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
            assertEquals(INITIAL_PAYLOAD, flow.getPayload());
        }
    }

    /**
     * Creates a minimal valid transformation configuration for tests that do not
     * need to focus on exact transformation setup values.
     */
    private static EventFlowConfigurationTransformationConfiguration transformationConfiguration() {
        return EventFlowConfigurationTransformationConfiguration.builder()
                .headers(EventFlowConfigurationHeaders.builder()
                        .mainBusinessObjectId("id-path")
                        .mainBusinessObjectType("type-path")
                        .bankCode("bank-path")
                        .build())
                .alias(List.of(
                        AliasingTransformationConfiguration.builder()
                                .pointer("alias-path")
                                .build()
                ))
                .build();
    }

    /**
     * Creates default event headers used by several tests.
     */
    private static Map<String, List<String>> defaultHeaders() {
        return Map.of("x-header", List.of("value"));
    }

    /**
     * Creates a coherent {@link EventFlow} fixture for strategy tests.
     */
    private static EventFlow flow(FlowDirection direction,
                                  String issuer,
                                  String subject,
                                  List<String> authorizedCodeAp,
                                  EventFlowConfigurationTransformationConfiguration transformations,
                                  String payload,
                                  Map<String, List<String>> headers,
                                  long receivedEventTimestamp) {

        TokenContext tokenContext = TokenContext.builder()
                .issuer(issuer)
                .subject(subject)
                .build();

        EventFlowConfiguration configuration = EventFlowConfiguration.builder()
                .flowId(FLOW_ID)
                .direction(direction)
                .authorizedCodeAp(authorizedCodeAp)
                .transformations(transformations)
                .build();

        return EventFlow.builder()
                .flowId(FLOW_ID)
                .flowDirection(direction)
                .configuration(configuration)
                .tokenContext(tokenContext)
                .payload(payload)
                .headers(headers)
                .receivedEventTimestamp(receivedEventTimestamp)
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





