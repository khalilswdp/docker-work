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

    @Test
    void shouldThrowUnauthorizedWhenAuthorizedSourcesIsNull() {
        ApiFlow flow = mockFlow(
                FlowDirection.IN,
                null,
                "issuer-a",
                "subject-a",
                null,
                null
        );

        GilCoreException ex = assertThrows(
                GilCoreException.class,
                () -> strategy.doProcessFlow(flow, forwardFlowPort)
        );

        assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getCode());
        verify(forwardFlowPort, never()).forwardFlow(any());
        verify(applyTransformationPort, never()).applyTransformation(any());
    }

    @Test
    void shouldThrowUnauthorizedWhenAuthorizedSourcesIsEmpty() {
        ApiFlow flow = mockFlow(
                FlowDirection.IN,
                Collections.emptyList(),
                "issuer-a",
                "subject-a",
                null,
                null
        );

        GilCoreException ex = assertThrows(
                GilCoreException.class,
                () -> strategy.doProcessFlow(flow, forwardFlowPort)
        );

        assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getCode());
        verify(forwardFlowPort, never()).forwardFlow(any());
        verify(applyTransformationPort, never()).applyTransformation(any());
    }

    @Test
    void shouldAuthorizeWhenAuthorizedSourcesContainsAllAndForwardWithoutTransformations() {
        ApiFlow flow = mockFlow(
                FlowDirection.IN,
                List.of("all"),
                "whatever-issuer",
                "whatever-subject",
                null,
                null
        );

        strategy.doProcessFlow(flow, forwardFlowPort);

        verify(forwardFlowPort).forwardFlow(flow);
        verify(applyTransformationPort, never()).applyTransformation(any());
    }

    @Test
    void shouldAuthorizeInAndApplyRequestAndResponseTransformationsInOrder() {
        ApiFlowConfigurationRequest requestConfig = mock(ApiFlowConfigurationRequest.class);
        ApiFlowConfigurationResponse responseConfig = mock(ApiFlowConfigurationResponse.class);

        // aliases used to verify ctx content
        List<AliasingTransformationConfiguration> requestAlias = List.of(new AliasingTransformationConfiguration("request-alis"));
        List<AliasingTransformationConfiguration> responseAlias = List.of(new AliasingTransformationConfiguration("resp-alias"));
        org.mockito.Mockito.when(requestConfig.getAlias()).thenReturn(requestAlias);
        org.mockito.Mockito.when(responseConfig.getAlias()).thenReturn(responseAlias);

        ApiFlow flow = mockFlow(
                FlowDirection.IN,
                List.of("trusted-issuer"),
                "trusted-issuer",
                "subject-a",
                requestConfig,
                responseConfig
        );

        strategy.doProcessFlow(flow, forwardFlowPort);

        InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);
        inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowRequestTransformationCtx.class));
        inOrder.verify(forwardFlowPort).forwardFlow(flow);
        inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowResponseTransformationCtx.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        // verify(applyTransformationPort, org.mockito.Mockito.times(2)).applyTransformation(captor.capture());

        Object firstCtx = captor.getAllValues().get(0);
        Object secondCtx = captor.getAllValues().get(1);

        ApiFlowRequestTransformationCtx requestCtx = (ApiFlowRequestTransformationCtx) firstCtx;
        ApiFlowResponseTransformationCtx responseCtx = (ApiFlowResponseTransformationCtx) secondCtx;

        assertEquals(flow.getRequest(), requestCtx.getApiFlowRequest());
        assertEquals(requestAlias, requestCtx.getAlias());
        assertEquals(FlowDirection.IN, requestCtx.getDirection());

        assertEquals(flow.getResponse(), responseCtx.getApiFlowResponse());
        assertEquals("resp-alias", responseCtx.getAlias());
        assertEquals(FlowDirection.IN, responseCtx.getDirection());
    }

    @Test
    void shouldAuthorizeInAndApplyOnlyRequestTransformationWhenResponseConfigIsNull() {
        ApiFlowConfigurationRequest requestConfig = mock(ApiFlowConfigurationRequest.class);
        List<AliasingTransformationConfiguration> requestAlias = List.of(new AliasingTransformationConfiguration("request-alis"));

        org.mockito.Mockito.when(requestConfig.getAlias()).thenReturn(requestAlias);

        ApiFlow flow = mockFlow(
                FlowDirection.IN,
                List.of("issuer-in"),
                "issuer-in",
                "subject-a",
                requestConfig,
                null
        );

        strategy.doProcessFlow(flow, forwardFlowPort);

        verify(applyTransformationPort, org.mockito.Mockito.times(1))
                .applyTransformation(any(ApiFlowRequestTransformationCtx.class));
        verify(forwardFlowPort).forwardFlow(flow);
    }

    @Test
    void shouldAuthorizeOutUsingApigeeIssuerAndSubjectMatch() {
        ApiFlowConfigurationResponse responseConfig = mock(ApiFlowConfigurationResponse.class);
        List<AliasingTransformationConfiguration> responseAlias = List.of(new AliasingTransformationConfiguration("request-alis"));

        org.mockito.Mockito.when(responseConfig.getAlias()).thenReturn(responseAlias);

        ApiFlow flow = mockFlow(
                FlowDirection.OUT,
                List.of("client-123"),
                "apigee",
                "client-123",
                null,
                responseConfig
        );

        strategy.doProcessFlow(flow, forwardFlowPort);

        verify(forwardFlowPort).forwardFlow(flow);
        verify(applyTransformationPort, org.mockito.Mockito.times(1))
                .applyTransformation(any(ApiFlowResponseTransformationCtx.class));
    }

    @Test
    void shouldThrowUnauthorizedForInWhenIssuerNotInAuthorizedSources() {
        ApiFlow flow = mockFlow(
                FlowDirection.IN,
                List.of("trusted-issuer"),
                "other-issuer",
                "subject-a",
                null,
                null
        );

        GilCoreException ex = assertThrows(
                GilCoreException.class,
                () -> strategy.doProcessFlow(flow, forwardFlowPort)
        );

        assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getCode());
        verify(forwardFlowPort, never()).forwardFlow(any());
        verify(applyTransformationPort, never()).applyTransformation(any());
    }

    @Test
    void shouldThrowUnauthorizedForOutWhenIssuerIsNotApigee() {
        ApiFlow flow = mockFlow(
                FlowDirection.OUT,
                List.of("client-123"),
                "other-issuer",
                "client-123",
                null,
                null
        );

        GilCoreException ex = assertThrows(
                GilCoreException.class,
                () -> strategy.doProcessFlow(flow, forwardFlowPort)
        );

        assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getCode());
        verify(forwardFlowPort, never()).forwardFlow(any());
        verify(applyTransformationPort, never()).applyTransformation(any());
    }

    @Test
    void shouldThrowUnauthorizedForOutWhenSubjectNotAuthorized() {
        ApiFlow flow = mockFlow(
                FlowDirection.OUT,
                List.of("client-999"),
                "apigee",
                "client-123",
                null,
                null
        );

        GilCoreException ex = assertThrows(
                GilCoreException.class,
                () -> strategy.doProcessFlow(flow, forwardFlowPort)
        );

        assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getCode());
        verify(forwardFlowPort, never()).forwardFlow(any());
        verify(applyTransformationPort, never()).applyTransformation(any());
    }

    private ApiFlow mockFlow(FlowDirection direction,
                             List<String> authorizedSources,
                             String issuer,
                             String subject,
                             ApiFlowConfigurationRequest requestTransformations,
                             ApiFlowConfigurationResponse responseTransformations) {
        ApiFlow flow = mock(ApiFlow.class);
        ApiFlowConfiguration configuration = mock(ApiFlowConfiguration.class);
        ApiFlowRequest request = mock(ApiFlowRequest.class);
        ApiFlowResponse response = mock(ApiFlowResponse.class);
        TokenContext tokenContext = mock(TokenContext.class);

        org.mockito.Mockito.when(flow.getConfiguration()).thenReturn(configuration);
        org.mockito.Mockito.when(flow.getRequest()).thenReturn(request);
        org.mockito.Mockito.when(flow.getResponse()).thenReturn(response);

        org.mockito.Mockito.when(configuration.getDirection()).thenReturn(direction);
        org.mockito.Mockito.when(configuration.getAuthorizedCodeAp()).thenReturn(authorizedSources);
        org.mockito.Mockito.when(configuration.getRequestTransformations()).thenReturn(requestTransformations);
        org.mockito.Mockito.when(configuration.getResponseTransformations()).thenReturn(responseTransformations);

        org.mockito.Mockito.when(request.getTokenContext()).thenReturn(tokenContext);
        org.mockito.Mockito.when(tokenContext.issuer()).thenReturn(issuer);
        org.mockito.Mockito.when(tokenContext.subject()).thenReturn(subject);

        return flow;
    }
}