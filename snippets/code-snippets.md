```
@Nested
class AuthorizationFailures {

    /**
     * Verifies that a missing or empty authorized source list is treated as
     * malformed configuration.
     */
    @ParameterizedTest(name = "{index} - malformed config: {0}")
    @MethodSource("com.example.ApiFlowProcessorStrategyImplTest#malformedAuthorizedCodeApCases")
    void shouldThrowCoreConfigMalformed_whenAuthorizedCodeApIsMissing(String ignoredCaseName,
                                                                      List<String> authorizedCodeAp) {
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


static Stream<Arguments> malformedAuthorizedCodeApCases() {
    return Stream.of(
            Arguments.of("authorizedCodeAp is null", null),
            Arguments.of("authorizedCodeAp is empty", List.of())
    );
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





