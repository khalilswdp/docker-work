```

## Title
Propagate `x-event-version` to Kafka event + refactor header enrichment

## Description

### What
- Add support for `x-event-version` HTTP header → mapped to `eventTypeVersion` in Kafka event
- Refactor `EnrichEventContentHeaderTransformationService` for readability and consistency
- Improve and extend unit tests

### Details
- `x-event-version` is now propagated to:

header.functional.eventType.eventTypeVersion

- Falls back to a default value when the header is missing or empty
- Centralized header extraction to avoid duplication
- Cleaner separation between technical and functional header enrichment

### Tests
- Added coverage for:
- header present vs missing
- empty header values
- default fallback behavior
- Introduced reusable assertion helpers
- Improved UUID validation

### Impact
- Enables dynamic event versioning from upstream requests
- Backward compatible (default still applied if header is absent)
- More robust handling of missing/blank headers
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





