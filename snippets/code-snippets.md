```

@RequestMapping(EstreemNotificationControllerAdapter.API_GATEWAY_ESTREEM_PATH_PREFIX)
@RestController
@RequiredArgsConstructor
public class EstreemNotificationControllerAdapter {

    public static final String API_GATEWAY_ESTREEM_PATH_PREFIX = "/bcef/v1/notifications";

    private static final String FLOW_ID_HTTP_HEADER_KEY = "flow_id";

    private static final String FLOW_ID_REGEX = "^[a-zA-Z0-9_-]{5,15}$";
    public static final String ESTREEM_ISS_REGEX = "^[A-Za-z0-9_\\-/]{3,15}$";
    public static final String ESTREEM_SUB_REGEX = "^[A-Za-z0-9_\\-/]{3,15}$";

    private final FromPartnerToBnppUseCase fromPartnerToBnppUseCase;

    @PostMapping(value = "/**")
    public ResponseEntity<Object> notifications(HttpServletRequest request, @RequestBody String body, @RequestHeader HttpHeaders httpHeaders) {
        Map<String, List<String>> convertedHttpHeaders = HttpHeadersUtil.fromHttpHeaders(httpHeaders);

        TokenContext requestTokenContext = JwtTokenUtil.getTokenContext(request);

        validToken(requestTokenContext);

        String flowId = this.extractFlowIdFromHeaders(convertedHttpHeaders);

        if (!isPayloadCorrect(body)) {
            throw new InvalidPayloadException("The body received in the request is incorrect");
        }

        EventFlow eventFlow = EventFlow.builder()
                .flowId(flowId)
                .receivedEventTimestamp(this.getCurrentTimestamp())
                .headers(convertedHttpHeaders)
                .payload(body)
                .flowDirection(FlowDirection.OUT)
                .tokenContext(requestTokenContext)
                .build();

        this.fromPartnerToBnppUseCase.doPipeline(eventFlow);

        return ResponseEntity.status(201).build();
    }

    private long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }

    private String extractFlowIdFromHeaders(Map<String, List<String>> httpHeaders) {
        Iterator<String> iteratorString = httpHeaders.get(FLOW_ID_HTTP_HEADER_KEY).iterator();

        if (!iteratorString.hasNext()) {
            throw new FlowIdResolutionException("The FlowId of the Event is missing in the request");
        }

        final String flowId = iteratorString.next();

        if (iteratorString.hasNext()) {
            throw new FlowIdResolutionException("More than one FlowId is present in the httpheaders");
        }

        if (!this.isFlowIdCorrect(flowId)) {
            throw new FlowIdResolutionException("The flowId does not respect constraints");
        }

        return flowId;
    }

    private boolean isFlowIdCorrect(String flowId) {
        return flowId != null && flowId.matches(FLOW_ID_REGEX);
    }

    private boolean isPayloadCorrect(String content) {
        return !content.isBlank();
    }

    private static void validToken(TokenContext requestTokenContext) {
        Pattern issuerPattern = Pattern.compile(ESTREEM_ISS_REGEX);

        String issuer = requestTokenContext.issuer();
        String subject = requestTokenContext.subject();

        if (issuer == null) {
            throw new GilFlowException(GilErrorCode.INVALID_TOKEN, "Invalid is mandatory... not present in the request");
        }

        Matcher issuerMatcher = issuerPattern.matcher(issuer);
        if (!issuerMatcher.matches()) {
            throw new GilFlowException(GilErrorCode.INVALID_TOKEN, "Invalid issuer: is mandatory... do not match regex");
        }

        if (subject != null) {
            Pattern subjectPattern = Pattern.compile(ESTREEM_SUB_REGEX);
            Matcher subjectMatcher = subjectPattern.matcher(subject);
            if (!subjectMatcher.matches()) {
                throw new GilFlowException(GilErrorCode.INVALID_TOKEN, "Invalid Subject: is optional... but if present, it should match subject regex");
            }

        }
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





