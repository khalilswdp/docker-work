```
/**
 * Validates whether the given API flow is authorized to be processed
 * based on its configuration and token context.
 *
 * <p>The authorization logic follows these rules:
 *
 * <ul>
 *     <li>If {@code authorizedCodeAp} is {@code null} or empty, the configuration is considered invalid
 *     and a {@link GilCoreException} with code {@link GilErrorCode#CORE_CONFIG_MALFORMED} is thrown.</li>
 *
 *     <li>If the list contains the special value {@code "all"}, the flow is always authorized
 *     regardless of direction, issuer, or subject.</li>
 *
 *     <li><b>IN flow:</b>
 *         <ul>
 *             <li>The flow is authorized if and only if the token {@code issuer} is present in
 *             {@code authorizedCodeAp}.</li>
 *             <li>The {@code subject} is ignored for authorization in this case.</li>
 *         </ul>
 *     </li>
 *
 *     <li><b>OUT flow:</b>
 *         <ul>
 *             <li>The flow is authorized if:
 *                 <ul>
 *                     <li>{@code issuer} is present in {@code authorizedCodeAp}, and</li>
 *                     <li>{@code subject} is either {@code null} or also present in {@code authorizedCodeAp}.</li>
 *                 </ul>
 *             </li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <p>If none of the above conditions are met, the flow is rejected and a
 * {@link GilCoreException} with code {@link GilErrorCode#AUTHENTICATION_TOKEN_FAILED} is thrown.
 *
 * @param flow the API flow being processed, containing direction and token context
 * @param apiFlowConfiguration the flow configuration containing authorized sources
 *
 * @throws GilCoreException if the configuration is malformed or the flow is not authorized
 */
 
 /**
 * Validates whether the given event flow is authorized to be processed
 * based on its configuration and token context.
 *
 * <p>The authorization logic follows these rules:
 *
 * <ul>
 *     <li>If {@code authorizedCodeAp} is {@code null} or empty, the configuration is considered invalid
 *     and a {@link GilCoreException} with code {@link GilErrorCode#CORE_CONFIG_MALFORMED} is thrown.</li>
 *
 *     <li>If the list contains the special value {@code "all"}, the flow is always authorized
 *     regardless of direction, issuer, or subject.</li>
 *
 *     <li><b>Event flows are only supported in {@link FlowDirection#OUT} direction.</b>
 *         <ul>
 *             <li>If the flow direction is {@code IN} and {@code "all"} is not configured,
 *             the flow is rejected.</li>
 *         </ul>
 *     </li>
 *
 *     <li><b>OUT flow:</b>
 *         <ul>
 *             <li>The flow is authorized if:
 *                 <ul>
 *                     <li>{@code issuer} is present in {@code authorizedCodeAp}, and</li>
 *                     <li>{@code subject} is either {@code null} or also present in {@code authorizedCodeAp}.</li>
 *                 </ul>
 *             </li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <p>If none of the above conditions are met, the flow is rejected and a
 * {@link GilCoreException} with code {@link GilErrorCode#AUTHENTICATION_TOKEN_FAILED} is thrown.
 *
 * @param flow the event flow being processed, containing direction and token context
 * @param flowConfiguration the flow configuration containing authorized sources
 *
 * @throws GilCoreException if the configuration is malformed or the flow is not authorized
 */

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





