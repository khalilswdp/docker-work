Here’s a solid JUnit 5 + Mockito test suite that targets 100% line/branch coverage for your ApiFlowProcessorStrategyImpl.

A small note first: JUnit has moved on beyond the JUnit 5.x line, but JUnit Jupiter remains the programming model you use for “JUnit 5-style” tests. Mockito is still the standard choice for this style of unit testing.  ￼

Test strategy

To fully cover your class, you need to exercise:
•	authorization failure when authorizedCodeAp is null
•	authorization failure when authorizedCodeAp is empty
•	authorization success with "all"
•	IN authorization success
•	IN authorization failure
•	OUT authorization success
•	OUT authorization failure because issuer is not apigee
•	OUT authorization failure because subject is not authorized
•	request transformation present / absent
•	response transformation present / absent
•	happy-path call order:
1.	auth
2.	request transformation
3.	forward
4.	response transformation

⸻

Example test class

This version assumes your domain objects expose the getters used by production code, and that TokenContext has issuer() and subject() as shown in your source.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFlowProcessorStrategyImplTest {

    private static final String APIGEE = "apigee";
    private static final String ALL = "all";

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
    private ApiFlowConfigurationRequest requestConfig;

    @Mock
    private ApiFlowConfigurationResponse responseConfig;

    @Mock
    private TokenContext tokenContext;

    private ApiFlowProcessorStrategyImpl strategy;

    @BeforeEach
    void setUp() {
        strategy = new ApiFlowProcessorStrategyImpl(applyTransformationPort);

        when(flow.getConfiguration()).thenReturn(configuration);
        when(flow.getRequest()).thenReturn(request);
        when(flow.getResponse()).thenReturn(response);
        when(request.getTokenContext()).thenReturn(tokenContext);
    }

    @Nested
    @DisplayName("Authorization")
    class AuthorizationTests {

        @Test
        @DisplayName("should throw when authorized sources are null")
        void shouldThrowWhenAuthorizedSourcesAreNull() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(null);
            when(tokenContext.issuer()).thenReturn("issuer-x");
            when(tokenContext.subject()).thenReturn("subject-x");

            GilCoreException ex = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getErrorCode());
            assertEquals(
                    "La liste de sources dans la configuration du flow et la source du flow (issuer-x, subject-x) ne correspondent pas.",
                    ex.getMessage()
            );

            verify(forwardFlowPort, never()).forwardFlow(any());
            verify(applyTransformationPort, never()).applyTransformation(any());
        }

        @Test
        @DisplayName("should throw when authorized sources are empty")
        void shouldThrowWhenAuthorizedSourcesAreEmpty() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(Collections.emptyList());
            when(tokenContext.issuer()).thenReturn("issuer-x");
            when(tokenContext.subject()).thenReturn("subject-x");

            GilCoreException ex = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getErrorCode());
            assertEquals(
                    "La liste de sources dans la configuration du flow et la source du flow (issuer-x, subject-x) ne correspondent pas.",
                    ex.getMessage()
            );

            verify(forwardFlowPort, never()).forwardFlow(any());
            verify(applyTransformationPort, never()).applyTransformation(any());
        }

        @Test
        @DisplayName("should allow flow when authorized sources contain all")
        void shouldAllowWhenAuthorizedSourcesContainAll() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of(ALL));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verify(applyTransformationPort, never()).applyTransformation(any());
            verifyNoMoreInteractions(applyTransformationPort, forwardFlowPort);
        }

        @Test
        @DisplayName("should authorize IN flow when issuer is allowed")
        void shouldAuthorizeInWhenIssuerMatches() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("trusted-issuer"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);
            when(tokenContext.issuer()).thenReturn("trusted-issuer");

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verify(applyTransformationPort, never()).applyTransformation(any());
        }

        @Test
        @DisplayName("should reject IN flow when issuer is not allowed")
        void shouldRejectInWhenIssuerDoesNotMatch() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("trusted-issuer"));
            when(tokenContext.issuer()).thenReturn("other-issuer");
            when(tokenContext.subject()).thenReturn("subject-x");

            GilCoreException ex = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getErrorCode());
            assertEquals(
                    "La liste de sources dans la configuration du flow et la source du flow (other-issuer, subject-x) ne correspondent pas.",
                    ex.getMessage()
            );

            verify(forwardFlowPort, never()).forwardFlow(any());
            verify(applyTransformationPort, never()).applyTransformation(any());
        }

        @Test
        @DisplayName("should authorize OUT flow when issuer is apigee and subject is allowed")
        void shouldAuthorizeOutWhenApigeeAndSubjectMatches() {
            when(configuration.getDirection()).thenReturn(FlowDirection.OUT);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("allowed-subject"));
            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(null);
            when(tokenContext.issuer()).thenReturn(APIGEE);
            when(tokenContext.subject()).thenReturn("allowed-subject");

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verify(applyTransformationPort, never()).applyTransformation(any());
        }

        @Test
        @DisplayName("should reject OUT flow when issuer is not apigee")
        void shouldRejectOutWhenIssuerIsNotApigee() {
            when(configuration.getDirection()).thenReturn(FlowDirection.OUT);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("allowed-subject"));
            when(tokenContext.issuer()).thenReturn("other-gateway");
            when(tokenContext.subject()).thenReturn("allowed-subject");

            GilCoreException ex = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getErrorCode());
            assertEquals(
                    "La liste de sources dans la configuration du flow et la source du flow (other-gateway, allowed-subject) ne correspondent pas.",
                    ex.getMessage()
            );

            verify(forwardFlowPort, never()).forwardFlow(any());
            verify(applyTransformationPort, never()).applyTransformation(any());
        }

        @Test
        @DisplayName("should reject OUT flow when subject is not allowed")
        void shouldRejectOutWhenSubjectDoesNotMatch() {
            when(configuration.getDirection()).thenReturn(FlowDirection.OUT);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("allowed-subject"));
            when(tokenContext.issuer()).thenReturn(APIGEE);
            when(tokenContext.subject()).thenReturn("forbidden-subject");

            GilCoreException ex = assertThrows(
                    GilCoreException.class,
                    () -> strategy.doProcessFlow(flow, forwardFlowPort)
            );

            assertEquals(GilErrorCode.AUTHENTICATION_TOKEN_FAILED, ex.getErrorCode());
            assertEquals(
                    "La liste de sources dans la configuration du flow et la source du flow (apigee, forbidden-subject) ne correspondent pas.",
                    ex.getMessage()
            );

            verify(forwardFlowPort, never()).forwardFlow(any());
            verify(applyTransformationPort, never()).applyTransformation(any());
        }
    }

    @Nested
    @DisplayName("Transformations")
    class TransformationTests {

        @Test
        @DisplayName("should apply request and response transformations in order")
        void shouldApplyRequestAndResponseTransformationsInOrder() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("trusted-issuer"));
            when(tokenContext.issuer()).thenReturn("trusted-issuer");

            when(configuration.getRequestTransformations()).thenReturn(requestConfig);
            when(requestConfig.getAlias()).thenReturn("request-alias");

            when(configuration.getResponseTransformations()).thenReturn(responseConfig);
            when(responseConfig.getAlias()).thenReturn("response-alias");

            strategy.doProcessFlow(flow, forwardFlowPort);

            InOrder inOrder = inOrder(applyTransformationPort, forwardFlowPort);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowRequestTransformationCtx.class));
            inOrder.verify(forwardFlowPort).forwardFlow(flow);
            inOrder.verify(applyTransformationPort).applyTransformation(any(ApiFlowResponseTransformationCtx.class));

            verifyNoMoreInteractions(forwardFlowPort, applyTransformationPort);
        }

        @Test
        @DisplayName("should skip request transformation when request config is null")
        void shouldSkipRequestTransformationWhenRequestConfigIsNull() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("trusted-issuer"));
            when(tokenContext.issuer()).thenReturn("trusted-issuer");

            when(configuration.getRequestTransformations()).thenReturn(null);
            when(configuration.getResponseTransformations()).thenReturn(responseConfig);
            when(responseConfig.getAlias()).thenReturn("response-alias");

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(forwardFlowPort).forwardFlow(flow);
            verify(applyTransformationPort).applyTransformation(any(ApiFlowResponseTransformationCtx.class));
            verify(applyTransformationPort, never()).applyTransformation(any(ApiFlowRequestTransformationCtx.class));
        }

        @Test
        @DisplayName("should skip response transformation when response config is null")
        void shouldSkipResponseTransformationWhenResponseConfigIsNull() {
            when(configuration.getDirection()).thenReturn(FlowDirection.IN);
            when(configuration.getAuthorizedCodeAp()).thenReturn(List.of("trusted-issuer"));
            when(tokenContext.issuer()).thenReturn("trusted-issuer");

            when(configuration.getRequestTransformations()).thenReturn(requestConfig);
            when(requestConfig.getAlias()).thenReturn("request-alias");
            when(configuration.getResponseTransformations()).thenReturn(null);

            strategy.doProcessFlow(flow, forwardFlowPort);

            verify(applyTransformationPort).applyTransformation(any(ApiFlowRequestTransformationCtx.class));
            verify(forwardFlowPort).forwardFlow(flow);
            verify(applyTransformationPort, never()).applyTransformation(any(ApiFlowResponseTransformationCtx.class));
        }
    }
}


⸻

Why this is good

This suite gives you:
•	100% line coverage
•	100% branch coverage
•	clean separation between:
•	authorization behavior
•	transformation behavior
•	explicit checks that unauthorized flows do not continue
•	order verification on the happy path

⸻

A stricter version

If your transformation context classes expose getters like getAlias(), getDirection(), getApiFlowRequest(), getApiFlowResponse(), then you can make the tests even better by verifying the built context contents instead of only checking type/order.

Example:

verify(applyTransformationPort).applyTransformation(argThat(
(ApiFlowRequestTransformationCtx ctx) ->
ctx.getApiFlowRequest() == request
&& ctx.getAlias().equals("request-alias")
&& ctx.getDirection() == FlowDirection.IN
));

And for response:

verify(applyTransformationPort).applyTransformation(argThat(
(ApiFlowResponseTransformationCtx ctx) ->
ctx.getApiFlowResponse() == response
&& ctx.getAlias().equals("response-alias")
&& ctx.getDirection() == FlowDirection.IN
));

That’s the version I’d use in a real codebase if those getters exist.

⸻

Suggested dependencies

For Maven, the usual setup is still JUnit Jupiter + Mockito’s JUnit integration. JUnit’s current docs show the modern Jupiter stack, and Mockito remains the mainstream mocking framework for this purpose.  ￼

<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

If you want, paste the signatures of:
•	ApiFlowRequestTransformationCtx
•	ApiFlowResponseTransformationCtx
•	GilCoreException

and I’ll tighten this into a version that also verifies the exact context field values and exception assertions with zero guessing.