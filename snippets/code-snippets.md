```
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


```

```
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





