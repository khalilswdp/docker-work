```
package com.example;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@ExtendWith(MockitoExtension.class)
class EventFlowProcessorStrategyImplTest {

    @Mock
    private ApplyTransformationPort applyTransformationPort;

    @Mock
    private ForwardFlowPort forwardFlowPort;

    @Mock
    private EventFlow flow;

    @Mock
    private EventFlowConfiguration flowConfiguration;

    @Mock
    private TokenContext tokenContext;

    private EventFlowProcessorStrategyImpl strategy;

    @BeforeEach
    void setUp() {
        strategy = new EventFlowProcessorStrategyImpl(applyTransformationPort);

        // Minimal shared setup used by all tests.
        when(flow.getFlowConfiguration()).thenReturn(flowConfiguration);
    }

    /**
     * Groups tests related to authorization rules evaluated before any
     * transformation or forwarding is performed.
     */
    @Nested
    @DisplayName("Authorization")
    class AuthorizationTests {

        /**
         * The special authorized source "all" must allow the flow immediately,
         * regardless of direction or token source.
         *
         * <p>Once authorized, the strategy must:
         * <ul>
         *     <li>build a transformation context</li>
         *     <li>apply the transformation</li>
         *     <li>propagate the transformed payload back into the flow</li>
         *     <li>forward the flow</li>
         * </ul>
         */
        @Test
        void should_apply_transformation_update_payload_and_forward_when_authorized_sources_contains_all() {
            // Arrange: configuration explicitly authorizes all sources.
            EventFlowConfigurationTransformationConfiguration transformationConfiguration =
                    buildTransformationConfiguration();

            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(flowConfiguration.getTransformations()).thenReturn(transformationConfiguration);
            when(flow.getPayload()).thenReturn("initial-payload");
            when(flow.getHeaders()).thenReturn(Map.of("x-header", List.of("value")));
            when(flow.getReceivedEventTimestamp()).thenReturn(123456789L);

            // Simulate a transformation updating the payload carried by the context.
            doAnswer(invocation -> {
                EventFlowTransformationCtx ctx = invocation.getArgument(0);
                ctx.setEventPayload("transformed-payload");
                return null;
            }).when(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert: transformation result must be written back to the flow before forwarding.
            InOrder inOrder = inOrder(applyTransformationPort, flow, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));
            inOrder.verify(flow).setPayload("transformed-payload");
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
            verifyNoMoreInteractions(applyTransformationPort, flow, forwardFlowPort);
        }

        /**
         * In event flows, the non-"all" authorization path is valid only when:
         * <ul>
         *     <li>direction is OUT</li>
         *     <li>issuer is "apigee"</li>
         *     <li>subject is contained in the authorized sources list</li>
         * </ul>
         */
        @Test
        void should_apply_transformation_update_payload_and_forward_when_direction_is_out_and_issuer_is_apigee_and_subject_is_authorized() {
            // Arrange: valid OUT authorization path.
            stubIssuerAndSubject("apigee", "subject-1");
            EventFlowConfigurationTransformationConfiguration transformationConfiguration =
                    buildTransformationConfiguration();

            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));
            when(flowConfiguration.getTransformations()).thenReturn(transformationConfiguration);
            when(flow.getPayload()).thenReturn("initial-payload");
            when(flow.getHeaders()).thenReturn(Map.of("x-header", List.of("value")));
            when(flow.getReceivedEventTimestamp()).thenReturn(123456789L);

            doAnswer(invocation -> {
                EventFlowTransformationCtx ctx = invocation.getArgument(0);
                ctx.setEventPayload("transformed-payload");
                return null;
            }).when(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert: full happy-path execution order.
            InOrder inOrder = inOrder(applyTransformationPort, flow, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));
            inOrder.verify(flow).setPayload("transformed-payload");
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
            verifyNoMoreInteractions(applyTransformationPort, flow, forwardFlowPort);
        }

        /**
         * If direction is not OUT and "all" is not configured, the flow must be rejected.
         *
         * <p>This test also helps cover the authorization condition where the OUT
         * branch evaluates to false.
         */
        @Test
        void should_throw_when_direction_is_not_out_and_all_is_not_configured() {
            // Arrange
            stubIssuerAndSubject("apigee", "subject-1");
            when(flow.getFlowDirection()).thenReturn(FlowDirection.IN);
            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * In OUT direction, an issuer different from "apigee" must be rejected,
         * even if the subject is present in the authorized list.
         */
        @Test
        void should_throw_when_direction_is_out_and_issuer_is_not_apigee() {
            // Arrange
            stubIssuerAndSubject("not-apigee", "subject-1");
            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * In OUT direction, even with issuer "apigee", the subject must still be
         * explicitly authorized.
         */
        @Test
        void should_throw_when_direction_is_out_and_subject_is_not_authorized() {
            // Arrange
            stubIssuerAndSubject("apigee", "subject-1");
            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("another-subject"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }
    }

    /**
     * Groups tests focused on the transformation context built and sent to the
     * transformation port.
     */
    @Nested
    @DisplayName("Transformation")
    class TransformationTests {

        /**
         * The strategy must build a transformation context from:
         * <ul>
         *     <li>configured aliases</li>
         *     <li>configured event header mapping</li>
         *     <li>flow payload</li>
         *     <li>flow headers</li>
         *     <li>received event timestamp</li>
         * </ul>
         */
        @Test
        void should_build_transformation_context_with_expected_values() {
            // Arrange
            stubIssuerAndSubject("apigee", "subject-1");

            EventFlowConfigurationHeaders headerConfiguration = EventFlowConfigurationHeaders.builder()
                    .mainBusinessObjectId("id-path")
                    .mainBusinessObjectType("type-path")
                    .bankCode("bank-path")
                    .build();

            EventFlowConfigurationTransformationConfiguration transformationConfiguration =
                    EventFlowConfigurationTransformationConfiguration.builder()
                            .headers(headerConfiguration)
                            .alias(List.of(
                                    AliasingTransformationConfiguration.builder()
                                            .pointer("alias-path")
                                            .build()
                            ))
                            .build();

            Map<String, List<String>> headers = Map.of("event-type", List.of("customer-created"));

            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));
            when(flowConfiguration.getTransformations()).thenReturn(transformationConfiguration);
            when(flow.getPayload()).thenReturn("event-payload");
            when(flow.getHeaders()).thenReturn(headers);
            when(flow.getReceivedEventTimestamp()).thenReturn(999L);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert: capture the exact transformation context sent to the port.
            ArgumentCaptor<EventFlowTransformationCtx> captor =
                    ArgumentCaptor.forClass(EventFlowTransformationCtx.class);

            verify(applyTransformationPort).applyTransformation(captor.capture());

            EventFlowTransformationCtx ctx = captor.getValue();
            org.junit.jupiter.api.Assertions.assertEquals(
                    transformationConfiguration.getAlias(), ctx.getAlias());
            org.junit.jupiter.api.Assertions.assertEquals(
                    headerConfiguration, ctx.getEventFlowConfigurationHeaders());
            org.junit.jupiter.api.Assertions.assertEquals(
                    "event-payload", ctx.getEventPayload());
            org.junit.jupiter.api.Assertions.assertEquals(
                    headers, ctx.getHeaders());
            org.junit.jupiter.api.Assertions.assertEquals(
                    999L, ctx.getReceivedEventTimestamp());

            verify(flow).setPayload("event-payload");
            verify(forwardFlowPort).forwardFlow(flow);
        }

        /**
         * The transformed payload contained in the transformation context must be
         * copied back into the flow before it is forwarded.
         */
        @Test
        void should_write_back_transformed_payload_to_flow_before_forwarding() {
            // Arrange
            stubIssuerAndSubject("apigee", "subject-1");

            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));
            when(flowConfiguration.getTransformations()).thenReturn(buildTransformationConfiguration());
            when(flow.getPayload()).thenReturn("initial-payload");
            when(flow.getHeaders()).thenReturn(Map.of());
            when(flow.getReceivedEventTimestamp()).thenReturn(42L);

            doAnswer(invocation -> {
                EventFlowTransformationCtx ctx = invocation.getArgument(0);
                ctx.setEventPayload("updated-by-transformation");
                return null;
            }).when(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert: transformed payload must be written back before forwarding.
            InOrder inOrder = inOrder(applyTransformationPort, flow, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(EventFlowTransformationCtx.class));
            inOrder.verify(flow).setPayload("updated-by-transformation");
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
        }
    }

    /**
     * Groups tests that verify fail-fast behavior.
     */
    @Nested
    @DisplayName("Fail-fast behavior")
    class FailFastTests {

        /**
         * When authorization fails, the strategy must stop immediately:
         * <ul>
         *     <li>no transformation is applied</li>
         *     <li>payload is not modified</li>
         *     <li>the flow is not forwarded</li>
         * </ul>
         */
        @Test
        void should_not_transform_update_payload_or_forward_when_authorization_fails() {
            // Arrange
            stubIssuerAndSubject("apigee", "subject-1");
            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(flowConfiguration.getAuthorizedCodeAp()).thenReturn(List.of("forbidden-subject"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
            verify(flow, org.mockito.Mockito.never()).setPayload(any());
        }
    }

    /**
     * Creates a minimal valid transformation configuration used by tests that
     * do not need to focus on the exact configuration values.
     */
    private EventFlowConfigurationTransformationConfiguration buildTransformationConfiguration() {
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
     * Stubs the token context required by authorization checks.
     */
    private void stubIssuerAndSubject(String issuer, String subject) {
        when(flow.getTokenContext()).thenReturn(tokenContext);
        when(tokenContext.issuer()).thenReturn(issuer);
        when(tokenContext.subject()).thenReturn(subject);
    }
}

```

