```
package com.example;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.*;
import org.mockito.InOrder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ApiFlowProcessorStrategyImpl}.
 *
 * <p>This suite focuses on the observable behavior of the strategy:
 * <ul>
 *     <li>authorization rules</li>
 *     <li>request/response transformation triggering</li>
 *     <li>overall orchestration order</li>
 *     <li>fail-fast behavior when authorization fails</li>
 * </ul>
 *
 * <p>Tests are grouped by responsibility using nested classes so that each
 * behavior is easy to locate and evolve.
 */
@ExtendWith(MockitoExtension.class)
class ApiFlowProcessorStrategyImplTest {

    @Mock
    private ApplyTransformationPort applyTransformationPort;

    @Mock
    private ForwardFlowPort forwardFlowPort;

    @Mock
    private ApiFlow flow;

    @Mock
    private ApiFlowConfiguration configuration;

    @Mock
    private ApiFlowRequest request;

    @Mock
    private ApiFlowResponse response;

    @Mock
    private TokenContext tokenContext;

    @Mock
    private ApiFlowConfigurationRequest requestTransformations;

    @Mock
    private ApiFlowConfigurationResponse responseTransformations;

    private ApiFlowProcessorStrategyImpl strategy;

    @BeforeEach
    void setUp() {
        strategy = new ApiFlowProcessorStrategyImpl(applyTransformationPort);

        // Minimal shared setup for all tests.
        when(flow.getFlowConfiguration()).thenReturn(configuration);
        when(flow.getConfiguration()).thenReturn(configuration);
    }

    /**
     * Groups tests related to the authorization rules enforced before any
     * transformation or forwarding is performed.
     */
    @Nested
    @DisplayName("Authorization")
    class AuthorizationTests {

        /**
         * The special source "all" should authorize the flow immediately,
         * without requiring issuer/subject checks.
         */
        @Test
        void should_forward_flow_when_authorized_sources_contains_all() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * In IN direction, authorization must be based on the token issuer.
         */
        @Test
        void should_forward_flow_when_direction_is_in_and_issuer_is_authorized() {
            // Arrange
            stubIssuer("issuer-1");
            when(flow.getFlowDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("issuer-1"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * In IN direction, if the issuer is not in the authorized source list,
         * the flow must be rejected immediately.
         */
        @Test
        void should_throw_when_direction_is_in_and_issuer_is_not_authorized() {
            // Arrange
            stubTokenContext("issuer-1", "subject-1");
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("another-issuer"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            // No side effect should happen after an authorization failure.
            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * In OUT direction, authorization must succeed only when:
         * <ul>
         *     <li>issuer is "apigee"</li>
         *     <li>subject is present in the authorized source list</li>
         * </ul>
         */
        @Test
        void should_forward_flow_when_direction_is_out_and_issuer_is_apigee_and_subject_is_authorized() {
            // Arrange
            stubTokenContext("apigee", "subject-1");
            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * In OUT direction, any issuer other than "apigee" must be rejected,
         * even when the subject is otherwise present in the authorized list.
         */
        @Test
        void should_throw_when_direction_is_out_and_issuer_is_not_apigee() {
            // Arrange
            stubTokenContext("not-apigee", "subject-1");
            when(configuration.getDirection()).thenReturn(FlowDirection.OUT);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * In OUT direction, even with issuer "apigee", the subject must still
         * be explicitly authorized.
         */
        @Test
        void should_throw_when_direction_is_out_and_subject_is_not_authorized() {
            // Arrange
            stubTokenContext("apigee", "subject-1");
            when(configuration.getDirection()).thenReturn(FlowDirection.OUT);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("another-subject"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * Documents the behavior currently observed in the implementation:
         * a null authorized source list triggers a {@link NullPointerException}
         * before a domain exception is raised.
         *
         * <p>If the production code is fixed later to throw a business exception
         * instead, this test should be updated accordingly.
         */
        @Test
        void should_throw_null_pointer_when_authorized_sources_is_null_with_current_implementation() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(null);

            // Act / Assert
            assertThrows(NullPointerException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * An empty authorized source list should reject the flow because there
         * is no matching allowed source.
         */
        @Test
        void should_throw_when_authorized_sources_is_empty() {
            // Arrange
            stubTokenContext("issuer-1", "subject-1");
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(Collections.emptyList());

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }
    }

    /**
     * Groups tests related only to request-side transformation triggering.
     */
    @Nested
    @DisplayName("Request transformations")
    class RequestTransformationTests {

        /**
         * No request transformation must be applied when the configuration is absent.
         */
        @Test
        void should_not_apply_request_transformation_when_request_configuration_is_null() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * A configured request transformation must be invoked before forwarding.
         */
        @Test
        void should_apply_request_transformation_when_request_configuration_is_present() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(requestTransformations);
            when(configuration.getResponseTransformations()).thenReturn(null);
            ApiFlowConfigurationRequest requestConfig = ApiFlowConfigurationRequest.builder()
                    .alias(List.of(
                            AliasingTransformationConfiguration.builder()
                                    .pointer("test-pointer")
                                    .build()
                    ))
                    .build();

            when(configuration.getRequestTransformations()).thenReturn(requestConfig);
            when(flow.getRequest()).thenReturn(request);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowRequestTransformationCtx.class));
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
            verifyNoMoreInteractions(applyTransformationPort, forwardFlowPort);
        }
    }

    /**
     * Groups tests related only to response-side transformation triggering.
     */
    @Nested
    @DisplayName("Response transformations")
    class ResponseTransformationTests {

        /**
         * No response transformation must be applied when the configuration is absent.
         */
        @Test
        void should_not_apply_response_transformation_when_response_configuration_is_null() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * A configured response transformation must be invoked after forwarding.
         */
        @Test
        void should_apply_response_transformation_when_response_configuration_is_present() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(responseTransformations);
            ApiFlowConfigurationResponse responseConfig = ApiFlowConfigurationResponse.builder()
                    .alias(List.of(
                            AliasingTransformationConfiguration.builder()
                                    .pointer("response-pointer")
                                    .build()
                    ))
                    .build();

            when(configuration.getResponseTransformations()).thenReturn(responseConfig);
            when(flow.getResponse()).thenReturn(response);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            InOrder inOrder = inOrder(forwardFlowPort, applyTransformationPort);
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowResponseTransformationCtx.class));
            verifyNoMoreInteractions(applyTransformationPort, forwardFlowPort);
        }
    }

    /**
     * Groups tests that verify the global orchestration order of the strategy.
     */
    @Nested
    @DisplayName("Orchestration")
    class OrchestrationTests {

        /**
         * When authorization succeeds and no transformations are configured,
         * the strategy should only forward the flow.
         */
        @Test
        void should_forward_flow_without_transformations_when_authorized() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            verify(forwardFlowPort).forwardFlow(flow);
            verifyNoInteractions(applyTransformationPort);
        }

        /**
         * Request transformation must happen before the flow is forwarded.
         */
        @Test
        void should_apply_request_transformation_before_forwarding() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(requestTransformations);
            when(configuration.getResponseTransformations()).thenReturn(null);
            ApiFlowConfigurationRequest requestConfig = ApiFlowConfigurationRequest.builder()
                    .alias(List.of(
                            AliasingTransformationConfiguration.builder()
                                    .pointer("test-pointer")
                                    .build()
                    ))
                    .build();

            when(configuration.getRequestTransformations()).thenReturn(requestConfig);
            when(flow.getRequest()).thenReturn(request);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowRequestTransformationCtx.class));
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
        }

        /**
         * Response transformation must happen only after the flow is forwarded.
         */
        @Test
        void should_apply_response_transformation_after_forwarding() {
            // Arrange
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("all"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(responseTransformations);
            ApiFlowConfigurationResponse responseConfig = ApiFlowConfigurationResponse.builder()
                    .alias(List.of(
                            AliasingTransformationConfiguration.builder()
                                    .pointer("response-pointer")
                                    .build()
                    ))
                    .build();

            when(configuration.getResponseTransformations()).thenReturn(responseConfig);
            when(flow.getResponse()).thenReturn(response);

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            InOrder inOrder = inOrder(forwardFlowPort, applyTransformationPort);
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowResponseTransformationCtx.class));
        }

        /**
         * Full happy path: request transformation, then forwarding, then response transformation.
         */
        @Test
        void should_apply_request_transformation_then_forward_then_response_transformation() {
            // Arrange
            when(flow.getFlowDirection()).thenReturn(FlowDirection.OUT);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("subject-1"));

            ApiFlowConfigurationRequest requestConfig = ApiFlowConfigurationRequest.builder()
                    .alias(List.of(
                            AliasingTransformationConfiguration.builder()
                                    .pointer("test-pointer")
                                    .build()
                    ))
                    .build();

            ApiFlowConfigurationResponse responseConfig = ApiFlowConfigurationResponse.builder()
                    .alias(List.of(
                            AliasingTransformationConfiguration.builder()
                                    .pointer("response-pointer")
                                    .build()
                    ))
                    .build();

            when(configuration.getRequestTransformations()).thenReturn(requestConfig);
            when(configuration.getResponseTransformations()).thenReturn(responseConfig);

            when(flow.getRequest()).thenReturn(request);
            when(flow.getResponse()).thenReturn(response);
            when(request.getTokenContext()).thenReturn(tokenContext);
            when(tokenContext.issuer()).thenReturn("apigee");
            when(tokenContext.subject()).thenReturn("subject-1");

            // Act
            strategy.doProcessFlow(flow, forwardFlowPort);

            // Assert
            InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowRequestTransformationCtx.class));
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowResponseTransformationCtx.class));
            verifyNoMoreInteractions(applyTransformationPort, forwardFlowPort);
        }

        /**
         * If authorization fails, the strategy must stop immediately:
         * no forwarding and no transformation should happen.
         */
        @Test
        void should_not_forward_or_transform_when_authorization_fails() {
            // Arrange
            stubIssuer("issuer-1");
            when(flow.getFlowDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("forbidden-source"));

            // Act / Assert
            assertThrows(GilCoreException.class, () -> strategy.doProcessFlow(flow, forwardFlowPort));

            verifyNoInteractions(applyTransformationPort, forwardFlowPort);
        }
    }

    /**
     * Shared helper used only by tests that need issuer/subject-based authorization.
     */
    private void stubTokenContext(String issuer, String subject) {
        when(flow.getRequest()).thenReturn(request);
        when(request.getTokenContext()).thenReturn(tokenContext);
        when(tokenContext.issuer()).thenReturn(issuer);
        when(tokenContext.subject()).thenReturn(subject);
    }


    private void stubIssuer(String issuer) {
        when(flow.getRequest()).thenReturn(request);
        when(request.getTokenContext()).thenReturn(tokenContext);
        when(tokenContext.issuer()).thenReturn(issuer);
    }

    private void stubIssuerAndSubject(String issuer, String subject) {
        when(flow.getRequest()).thenReturn(request);
        when(request.getTokenContext()).thenReturn(tokenContext);
        when(tokenContext.issuer()).thenReturn(issuer);
        when(tokenContext.subject()).thenReturn(subject);
    }
}
```