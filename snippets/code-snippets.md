```
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





