```

/**
 * Unit tests for {@link EnrichEventContentHeaderTransformationService}.
 *
 * <p>The tests cover:
 * <ul>
 *     <li>nominal enrichment when all headers are present,</li>
 *     <li>fallback behavior when specific headers are missing or empty,</li>
 *     <li>verification of static values always injected into the event header.</li>
 * </ul>
 */
class EnrichEventContentHeaderTransformationServiceTest {

    /** Fixed input timestamp used by all tests. */
    private static final long RECEIVED_TIMESTAMP = 1713350400000L;

    private static final String EVENT_TECH_ID_HEADER_KEY = "eventtechid";
    private static final String X_REQUEST_ID_HEADER_KEY = "x-request-id";
    private static final String X_EVENT_VERSION_HEADER_KEY = "x-event-version";
    private static final String ITR_ID_HEADER_KEY = "itrId";

    private static final String EVENT_TECH_ID = "event-tech-123";
    private static final String MAIN_BUSINESS_OBJECT_ID = "main-bo-456";
    private static final String ITR_ID = "itr-789";

    private static final String HEADER_EVENT_VERSION = "eventVersion-from-header";
    private static final String DEFAULT_EVENT_VERSION = "eventVersion-tobe-defined";
    private static final String DEFAULT_ITR_ID = "ITRId_eventType_to_determine";

    private static final String EVENT_NORM_VERSION = "2.4.1";
    private static final String AP_CODE = "A100789";
    private static final String TIME_ZONE = "Europe/Paris";

    private static final String EVENT_TYPE_OE_ID = "OEId_eventType_to_determine";
    private static final String MAIN_BUSINESS_OBJECT_TYPE_OE_ID = "OEId_for_each_event";

    private EnrichEventContentHeaderTransformationService service;

    @BeforeEach
    void setUp() {
        service = new EnrichEventContentHeaderTransformationService();
    }

    /**
     * Tests the complete nominal path where every expected input header is present.
     */
    @Nested
    class NominalCase {

        @Test
        void should_enrich_all_headers_when_all_input_headers_are_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    headers(
                            header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                            header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                            header(ITR_ID_HEADER_KEY, ITR_ID),
                            header(X_EVENT_VERSION_HEADER_KEY, HEADER_EVENT_VERSION)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertTechnicalHeader(header);
            assertFunctionalHeader(header, ITR_ID, MAIN_BUSINESS_OBJECT_ID, HEADER_EVENT_VERSION);
            assertThat(header.getTechnical().getEventTechId()).isEqualTo(EVENT_TECH_ID);
        }
    }

    /**
     * Tests all behaviors related to resolution or generation of the technical event id.
     */
    @Nested
    class EventTechId {

        @Test
        void should_use_header_eventTechId_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    headers(
                            header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                            header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                            header(ITR_ID_HEADER_KEY, ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getTechnical().getEventTechId())
                    .isEqualTo(EVENT_TECH_ID);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidEventTechIdHeaders")
        void should_generate_eventTechId_when_header_is_missing_or_invalid(
                @SuppressWarnings("unused") String caseName,
                Map<String, List<String>> headers
        ) {
            EventFlowTransformationCtx ctx = buildContext(headers);

            service.enrichEventContentHeader(ctx);

            String generatedEventTechId = ctx.getEventContent().getHeader().getTechnical().getEventTechId();

            // Generated ids must be non-blank and valid UUIDs.
            assertIsUuid(generatedEventTechId);
        }

        private static Stream<Arguments> invalidEventTechIdHeaders() {
            return Stream.of(
                    namedHeaders(
                            "missing eventtechid header",
                            headers(
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    ),
                    namedHeaders(
                            "empty eventtechid header list",
                            headers(
                                    emptyHeader(EVENT_TECH_ID_HEADER_KEY),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    ),
                    namedHeaders(
                            "null first eventtechid value",
                            headers(
                                    nullFirstValueHeader(EVENT_TECH_ID_HEADER_KEY),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    ),
                    namedHeaders(
                            "blank first eventtechid value",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, "   "),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    )
            );
        }
    }

    /**
     * Tests extraction and fallback behavior for the main business object id.
     */
    @Nested
    class MainBusinessObjectId {

        @Test
        void should_set_mainBusinessObjectId_from_xRequestId_header() {
            EventFlowTransformationCtx ctx = buildContext(
                    headers(
                            header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                            header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                            header(ITR_ID_HEADER_KEY, ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(ctx.getEventContent().getHeader(), ITR_ID, MAIN_BUSINESS_OBJECT_ID, DEFAULT_EVENT_VERSION);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidMainBusinessObjectIdHeaders")
        void should_set_empty_mainBusinessObjectId_when_xRequestId_header_is_missing_or_invalid(
                @SuppressWarnings("unused") String caseName,
                Map<String, List<String>> headers
        ) {
            EventFlowTransformationCtx ctx = buildContext(headers);

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(ctx.getEventContent().getHeader(), ITR_ID, "", DEFAULT_EVENT_VERSION);
        }

        private static Stream<Arguments> invalidMainBusinessObjectIdHeaders() {
            return Stream.of(
                    namedHeaders(
                            "missing x-request-id header",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    ),
                    namedHeaders(
                            "empty x-request-id header list",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    emptyHeader(X_REQUEST_ID_HEADER_KEY),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    ),
                    namedHeaders(
                            "null first x-request-id value",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    nullFirstValueHeader(X_REQUEST_ID_HEADER_KEY),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    ),
                    namedHeaders(
                            "blank first x-request-id value",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, " "),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    )
            );
        }
    }

    /**
     * Tests extraction and fallback behavior for the ITR id,
     * which is reused in multiple functional blocks.
     */
    @Nested
    class ItrId {

        @Test
        void should_set_itrId_from_header_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    headers(
                            header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                            header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                            header(ITR_ID_HEADER_KEY, ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            // The resolved ITR id must be propagated consistently to both blocks.
            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId()).isEqualTo(ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId()).isEqualTo(ITR_ID);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidItrIdHeaders")
        void should_use_default_itrId_when_header_is_missing_or_invalid(
                @SuppressWarnings("unused") String caseName,
                Map<String, List<String>> headers
        ) {
            EventFlowTransformationCtx ctx = buildContext(headers);

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
        }

        private static Stream<Arguments> invalidItrIdHeaders() {
            return Stream.of(
                    namedHeaders(
                            "missing itrId header",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID)
                            )
                    ),
                    namedHeaders(
                            "empty itrId header list",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    emptyHeader(ITR_ID_HEADER_KEY)
                            )
                    ),
                    namedHeaders(
                            "null first itrId value",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    nullFirstValueHeader(ITR_ID_HEADER_KEY)
                            )
                    ),
                    namedHeaders(
                            "blank first itrId value",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, "   ")
                            )
                    )
            );
        }
    }

    /**
     * Tests extraction and fallback behavior for the functional event version.
     */
    @Nested
    class EventVersion {

        @Test
        void should_set_eventTypeVersion_from_header_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    headers(
                            header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                            header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                            header(ITR_ID_HEADER_KEY, ITR_ID),
                            header(X_EVENT_VERSION_HEADER_KEY, HEADER_EVENT_VERSION)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(ctx.getEventContent().getHeader(), ITR_ID, MAIN_BUSINESS_OBJECT_ID, HEADER_EVENT_VERSION);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidEventVersionHeaders")
        void should_use_default_eventTypeVersion_when_header_is_missing_or_invalid(
                @SuppressWarnings("unused") String caseName,
                Map<String, List<String>> headers
        ) {
            EventFlowTransformationCtx ctx = buildContext(headers);

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(
                    ctx.getEventContent().getHeader(),
                    ITR_ID,
                    MAIN_BUSINESS_OBJECT_ID,
                    DEFAULT_EVENT_VERSION
            );
        }

        private static Stream<Arguments> invalidEventVersionHeaders() {
            return Stream.of(
                    namedHeaders(
                            "missing x-event-version header",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID)
                            )
                    ),
                    namedHeaders(
                            "empty x-event-version header list",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID),
                                    emptyHeader(X_EVENT_VERSION_HEADER_KEY)
                            )
                    ),
                    namedHeaders(
                            "null first x-event-version value",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID),
                                    nullFirstValueHeader(X_EVENT_VERSION_HEADER_KEY)
                            )
                    ),
                    namedHeaders(
                            "blank first x-event-version value",
                            headers(
                                    header(EVENT_TECH_ID_HEADER_KEY, EVENT_TECH_ID),
                                    header(X_REQUEST_ID_HEADER_KEY, MAIN_BUSINESS_OBJECT_ID),
                                    header(ITR_ID_HEADER_KEY, ITR_ID),
                                    header(X_EVENT_VERSION_HEADER_KEY, " ")
                            )
                    )
            );
        }
    }

    /**
     * Ensures that static metadata is always present even when all optional headers are absent.
     */
    @Test
    void should_always_set_static_technical_and_functional_values() {
        EventFlowTransformationCtx ctx = buildContext(Map.of());

        service.enrichEventContentHeader(ctx);

        EventContentHeader header = ctx.getEventContent().getHeader();

        assertThat(header.getTechnical().getEventNormVersion()).isEqualTo(EVENT_NORM_VERSION);
        assertThat(header.getTechnical().getTimeZone()).isEqualTo(TIME_ZONE);
        assertThat(header.getTechnical().getApCode()).isEqualTo(AP_CODE);

        assertFunctionalHeader(header, DEFAULT_ITR_ID, "", DEFAULT_EVENT_VERSION);
    }

    /**
     * Verifies the technical header independently from dynamic functional fields.
     *
     * @param header the enriched header to verify
     */
    private void assertTechnicalHeader(EventContentHeader header) {
        assertThat(header.getTechnical()).isNotNull();
        assertThat(header.getTechnical().getEventNormVersion()).isEqualTo(EVENT_NORM_VERSION);
        assertThat(header.getTechnical().getTimeZone()).isEqualTo(TIME_ZONE);
        assertThat(header.getTechnical().getApCode()).isEqualTo(AP_CODE);

        // Timestamp is derived from the received epoch and should always be populated.
        assertThat(header.getTechnical().getTimestamp()).isNotBlank();
    }

    /**
     * Verifies the functional header against the expected resolved values.
     *
     * @param header the enriched header to verify
     * @param expectedItrId expected ITR id
     * @param expectedMainBusinessObjectId expected main business object id
     * @param expectedEventVersion expected event type version
     */
    private void assertFunctionalHeader(
            EventContentHeader header,
            String expectedItrId,
            String expectedMainBusinessObjectId,
            String expectedEventVersion
    ) {
        assertThat(header.getFunctional()).isNotNull();

        assertThat(header.getFunctional().getEventType()).isNotNull();
        assertThat(header.getFunctional().getEventType().getEventTypeVersion()).isEqualTo(expectedEventVersion);
        assertThat(header.getFunctional().getEventType().getEventTypeId().getOEId())
                .isEqualTo(EVENT_TYPE_OE_ID);
        assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                .isEqualTo(expectedItrId);

        assertThat(header.getFunctional().getMainBusinessObject()).isNotNull();
        assertThat(header.getFunctional().getMainBusinessObject().getId()).isEqualTo(expectedMainBusinessObjectId);
        assertThat(header.getFunctional().getMainBusinessObject().getType().getOEId())
                .isEqualTo(MAIN_BUSINESS_OBJECT_TYPE_OE_ID);
        assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                .isEqualTo(expectedItrId);

        // The service always initializes the collection, even when there is no secondary object.
        assertThat(header.getFunctional().getSecondaryBusinessObjects()).isNotNull().isEmpty();
    }

    /**
     * Builds a transformation context with the provided transport headers and
     * a minimally initialized event content.
     *
     * @param headers input transport headers
     * @return a ready-to-use transformation context for tests
     */
    private EventFlowTransformationCtx buildContext(Map<String, List<String>> headers) {
        EventFlowTransformationCtx ctx = new EventFlowTransformationCtx();
        ctx.setHeaders(headers);
        ctx.setReceivedEventTimestamp(RECEIVED_TIMESTAMP);
        ctx.setEventContent(buildEventContent());
        return ctx;
    }

    /**
     * Builds a minimal event content instance with initialized technical and functional headers.
     *
     * @return an event content object ready to be enriched
     */
    private EventContent buildEventContent() {
        EventContent eventContent = new EventContent();

        EventContentHeader header = new EventContentHeader();
        header.setTechnical(new TechnicalHeaderDef());
        header.setFunctional(new FunctionalHeaderDef());

        eventContent.setHeader(header);
        return eventContent;
    }

    /**
     * Builds a headers map from the provided entries.
     *
     * @param entries header entries to include
     * @return a mutable headers map
     */
    @SafeVarargs
    private static Map<String, List<String>> headers(Map.Entry<String, List<String>>... entries) {
        Map<String, List<String>> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : entries) {
            headers.put(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    /**
     * Builds a single-value header entry.
     *
     * @param key header name
     * @param value header first value
     * @return a header entry containing one value
     */
    private static Map.Entry<String, List<String>> header(String key, String value) {
        return Map.entry(key, List.of(value));
    }

    /**
     * Builds a header entry with an empty value list.
     *
     * @param key header name
     * @return a header entry containing no values
     */
    private static Map.Entry<String, List<String>> emptyHeader(String key) {
        return Map.entry(key, List.of());
    }

    /**
     * Builds a header entry whose first value is null.
     *
     * @param key header name
     * @return a header entry containing a null first value
     */
    private static Map.Entry<String, List<String>> nullFirstValueHeader(String key) {
        return new AbstractMap.SimpleEntry<>(key, new ArrayList<>(Collections.singletonList(null)));
    }

    /**
     * Creates a named parameterized-test argument pair.
     *
     * @param caseName displayed parameterized-test name
     * @param headers headers to inject in the test context
     * @return named arguments for a parameterized test
     */
    private static Arguments namedHeaders(
            String caseName,
            Map<String, List<String>> headers
    ) {
        return Arguments.of(Named.of(caseName, caseName), headers);
    }

    /**
     * Verifies that the given value is a non-blank valid UUID string.
     *
     * @param value generated identifier to validate
     */
    private static void assertIsUuid(String value) {
        assertThat(value).isNotBlank();
        assertThatCode(() -> {
            UUID uuid = UUID.fromString(value);
            assertThat(uuid).isNotNull();
        }).doesNotThrowAnyException();
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





