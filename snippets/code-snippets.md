```
#!/usr/bin/env bash
set -euo pipefail

SOURCE_IMAGE="provectuslabs/kafka-ui:v0.7.2"
TARGET_IMAGE="p-5141-docker-local/kafka-ui:v0.7.2"

echo "Pulling source image..."
docker pull "$SOURCE_IMAGE"

echo "Tagging image..."
docker tag "$SOURCE_IMAGE" "$TARGET_IMAGE"

echo "Pushing target image..."
docker push "$TARGET_IMAGE"

echo "Done: $TARGET_IMAGE"

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





