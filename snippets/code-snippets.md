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

        @ParameterizedTest
        @MethodSource("com.example.EventFlowProcessorStrategyImplTest#malformedAuthorizedCodeApCases")
        void shouldThrowCoreConfigMalformed_whenAuthorizedCodeApIsMissing(List<String> authorizedCodeAp) {
            EventFlow flow = flow(
                    FlowDirection.IN,
                    VALID_ISSUER,
                    VALID_SUBJECT,
                    authorizedCodeAp,
                    transformationConfiguration(),
                    INITIAL_PAYLOAD,
                    defaultHeaders(),
                    RECEIVED_EVENT_TIMESTAMP
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
            EventFlow flow = flow(
                    FlowDirection.IN,
                    "ap99999",
                    VALID_SUBJECT,
                    List.of("ap11111", "ap22222"),
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

        @Test
        void shouldProcessFlow_whenAuthorizedForAll() {
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

        @Test
        void shouldProcessFlow_whenInFlowIssuerIsAuthorized() {
            EventFlow flow = flow(
                    FlowDirection.IN,
                    VALID_ISSUER,
                    "subject-not-in-list",
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

        @Test
        void shouldProcessFlow_whenInFlowIssuerIsAuthorizedAndSubjectIsNull() {
            EventFlow flow = flow(
                    FlowDirection.IN,
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

        @Test
        void shouldApplyTransformationUpdatePayloadAndForward() {
            EventFlowConfigurationTransformationConfiguration transformationConfiguration = transformationConfiguration();

            EventFlow flow = flow(
                    FlowDirection.IN,
                    VALID_ISSUER,
                    VALID_SUBJECT,
                    List.of(VALID_ISSUER),
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

    static Stream<Arguments> malformedAuthorizedCodeApCases() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(List.of())
        );
    }

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

    private static Map<String, List<String>> defaultHeaders() {
        return Map.of("x-header", List.of("value"));
    }

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





