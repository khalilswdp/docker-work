```

@Slf4j
@Named
@RequiredArgsConstructor
public class EnrichEventContentHeaderTransformationService {

    private static final String EVENT_TECH_ID_HEADER_KEY = "eventtechid";
    private static final String X_REQUEST_ID_HEADER_KEY = "x-request-id";
    private static final String ITR_ID_HEADER_KEY = "itrId";

    private static final String AP_CODE = "A100789";
    private static final String EVENT_NORM_VERSION = "2.4.1";
    private static final String EVENT_TYPE_VERSION = "1.0.0";
    private static final String ZONE_ID = ZoneId.of("Europe/Paris").getId();

    private static final String DEFAULT_ITR_ID = "ITRId_eventType_to_determine";
    private static final String DEFAULT_EVENT_TYPE_OE_ID = "OEId_eventType_to_determine";
    private static final String DEFAULT_MAIN_BUSINESS_OBJECT_OE_ID = "OEId_for_each_event";

    private static final DateTimeUtil DATE_TIME_UTIL = new DateTimeUtil();

    public void enrichEventContentHeader(EventFlowTransformationCtx transformationCtx) {
        EventContent eventContent = transformationCtx.getEventContent();
        EventContentHeader header = eventContent.getHeader();
        Map<String, List<String>> headers = transformationCtx.getHeaders();

        String mainBusinessObjectId = getMainBusinessObjectId(headers);
        String itrId = getItrId(headers);

        enrichTechnicalHeader(header, headers, transformationCtx.getReceivedEventTimestamp());
        enrichFunctionalHeader(header, itrId, mainBusinessObjectId);
    }

    private void enrichTechnicalHeader(
            EventContentHeader header,
            Map<String, List<String>> headers,
            long receivedEventTimestamp
    ) {
        TechnicalHeaderDef technical = header.getTechnical();
        technical.setEventTechId(resolveEventTechId(headers));
        technical.setEventNormVersion(EVENT_NORM_VERSION);
        technical.setTimestamp(DATE_TIME_UTIL.convertEpochToIso8601(receivedEventTimestamp));
        technical.setTimeZone(ZONE_ID);
        technical.setApCode(AP_CODE);
    }

    private void enrichFunctionalHeader(
            EventContentHeader header,
            String itrId,
            String mainBusinessObjectId
    ) {
        FunctionalHeaderDef functional = header.getFunctional();
        functional.setEventType(buildEventType(itrId));
        functional.setMainBusinessObject(buildMainBusinessObject(itrId, mainBusinessObjectId));
        functional.setSecondaryBusinessObjects(new ArrayList<>());
    }

    private String getMainBusinessObjectId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, X_REQUEST_ID_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("{} is empty", X_REQUEST_ID_HEADER_KEY);
                    return "";
                });
    }

    private String getItrId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, ITR_ID_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("ItrId not present in headers");
                    return DEFAULT_ITR_ID;
                });
    }

    private String resolveEventTechId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, EVENT_TECH_ID_HEADER_KEY)
                .orElseGet(() -> {
                    String generatedEventTechId = UUID.randomUUID().toString();
                    log.warn(
                            "eventTechId not present in headers, generating eventTechId [{}]",
                            generatedEventTechId
                    );
                    return generatedEventTechId;
                });
    }

    private Optional<String> getFirstHeaderValue(Map<String, List<String>> headers, String key) {
        List<String> values = headers.get(key);
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(values.getFirst());
    }

    private EventTypeDef buildEventType(String itrId) {
        EventTypeDef eventTypeDef = new EventTypeDef();
        eventTypeDef.setEventTypeId(TypeIdDef.builder()
                .OEId(DEFAULT_EVENT_TYPE_OE_ID)
                .ITRId(itrId)
                .build());
        eventTypeDef.setEventTypeVersion(EVENT_TYPE_VERSION);
        return eventTypeDef;
    }

    private MainBusinessObjectDef buildMainBusinessObject(String itrId, String mainBusinessObjectId) {
        MainBusinessObjectDef mainBusinessObjectDef = new MainBusinessObjectDef();
        mainBusinessObjectDef.setId(mainBusinessObjectId);
        mainBusinessObjectDef.setType(TypeIdDef.builder()
                .ITRId(itrId)
                .OEId(DEFAULT_MAIN_BUSINESS_OBJECT_OE_ID)
                .build());
        return mainBusinessObjectDef;
    }
}
```

```
class EnrichEventContentHeaderTransformationServiceTest {

    private static final long RECEIVED_TIMESTAMP = 1713350400000L;

    private static final String EVENT_TECH_ID_HEADER_KEY = "eventtechid";
    private static final String X_REQUEST_ID_HEADER_KEY = "x-request-id";
    private static final String ITR_ID_HEADER_KEY = "itrId";

    private static final String EVENT_TECH_ID = "event-tech-123";
    private static final String MAIN_BUSSINESS_OBJECT_ID = "main-bo-456";
    private static final String ITR_ID = "itr-789";

    private static final String DEFAULT_ITR_ID = "ITRId_eventType_to_determine";
    private static final String EVENT_NORM_VERSION = "2.4.1";
    private static final String EVENT_TYPE_VERSION = "1.0.0";
    private static final String AP_CODE = "A100789";
    private static final String TIME_ZONE = "Europe/Paris";

    private EnrichEventContentHeaderTransformationService service;

    @BeforeEach
    void setUp() {
        service = new EnrichEventContentHeaderTransformationService();
    }

    @Nested
    class NominalCase {

        @Test
        void should_enrich_all_headers_when_all_input_headers_are_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertTechnicalHeader(header);
            assertFunctionalHeader(header, ITR_ID, MAIN_BUSSINESS_OBJECT_ID);
            assertThat(header.getTechnical().getEventTechId()).isEqualTo(EVENT_TECH_ID);
        }
    }

    @Nested
    class EventTechId {

        @Test
        void should_use_header_eventTechId_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getTechnical().getEventTechId())
                    .isEqualTo(EVENT_TECH_ID);
        }

        @Test
        void should_generate_eventTechId_when_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            String generatedEventTechId = ctx.getEventContent().getHeader().getTechnical().getEventTechId();

            assertThat(generatedEventTechId).isNotBlank();
            assertThatCode(() -> UUID.fromString(generatedEventTechId)).doesNotThrowAnyException();
        }

        @Test
        void should_generate_eventTechId_when_header_value_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            String generatedEventTechId = ctx.getEventContent().getHeader().getTechnical().getEventTechId();

            assertThat(generatedEventTechId).isNotBlank();
            assertThatCode(() -> UUID.fromString(generatedEventTechId)).doesNotThrowAnyException();
        }
    }

    @Nested
    class MainBusinessObjectId {

        @Test
        void should_set_mainBusinessObjectId_from_xRequestId_header() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getMainBusinessObject().getId())
                    .isEqualTo(MAIN_BUSSINESS_OBJECT_ID);
        }

        @Test
        void should_set_empty_mainBusinessObjectId_when_xRequestId_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getMainBusinessObject().getId())
                    .isEmpty();
        }

        @Test
        void should_set_empty_mainBusinessObjectId_when_xRequestId_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getMainBusinessObject().getId())
                    .isEmpty();
        }
    }

    @Nested
    class ItrId {

        @Test
        void should_set_itrId_from_header_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId()).isEqualTo(ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId()).isEqualTo(ITR_ID);
        }

        @Test
        void should_use_default_itrId_when_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
        }

        @Test
        void should_use_default_itrId_when_header_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of()
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
        }
    }

    @Test
    void should_always_set_static_technical_and_functional_values() {
        EventFlowTransformationCtx ctx = buildContext(Map.of());

        service.enrichEventContentHeader(ctx);

        EventContentHeader header = ctx.getEventContent().getHeader();

        assertThat(header.getTechnical().getEventNormVersion()).isEqualTo(EVENT_NORM_VERSION);
        assertThat(header.getTechnical().getTimeZone()).isEqualTo(TIME_ZONE);
        assertThat(header.getTechnical().getApCode()).isEqualTo(AP_CODE);

        assertThat(header.getFunctional().getEventType().getEventTypeVersion()).isEqualTo(EVENT_TYPE_VERSION);
        assertThat(header.getFunctional().getEventType().getEventTypeId().getOEId())
                .isEqualTo("OEId_eventType_to_determine");
        assertThat(header.getFunctional().getMainBusinessObject().getType().getOEId())
                .isEqualTo("OEId_for_each_event");
        assertThat(header.getFunctional().getSecondaryBusinessObjects()).isNotNull().isEmpty();
    }

    private void assertTechnicalHeader(EventContentHeader header) {
        assertThat(header.getTechnical()).isNotNull();
        assertThat(header.getTechnical().getEventNormVersion()).isEqualTo(EVENT_NORM_VERSION);
        assertThat(header.getTechnical().getTimeZone()).isEqualTo(TIME_ZONE);
        assertThat(header.getTechnical().getApCode()).isEqualTo(AP_CODE);
        assertThat(header.getTechnical().getTimestamp()).isNotBlank();
    }

    private void assertFunctionalHeader(EventContentHeader header, String expectedItrId, String expectedMainBusinessObjectId) {
        assertThat(header.getFunctional()).isNotNull();

        assertThat(header.getFunctional().getEventType()).isNotNull();
        assertThat(header.getFunctional().getEventType().getEventTypeVersion()).isEqualTo(EVENT_TYPE_VERSION);
        assertThat(header.getFunctional().getEventType().getEventTypeId().getOEId())
                .isEqualTo("OEId_eventType_to_determine");
        assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                .isEqualTo(expectedItrId);

        assertThat(header.getFunctional().getMainBusinessObject()).isNotNull();
        assertThat(header.getFunctional().getMainBusinessObject().getId()).isEqualTo(expectedMainBusinessObjectId);
        assertThat(header.getFunctional().getMainBusinessObject().getType().getOEId())
                .isEqualTo("OEId_for_each_event");
        assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                .isEqualTo(expectedItrId);

        assertThat(header.getFunctional().getSecondaryBusinessObjects()).isNotNull().isEmpty();
    }

    private EventFlowTransformationCtx buildContext(Map<String, List<String>> headers) {
        EventFlowTransformationCtx ctx = new EventFlowTransformationCtx();
        ctx.setHeaders(headers);
        ctx.setReceivedEventTimestamp(RECEIVED_TIMESTAMP);
        ctx.setEventContent(buildEventContent());
        return ctx;
    }

    private EventContent buildEventContent() {
        EventContent eventContent = new EventContent();

        EventContentHeader header = new EventContentHeader();
        header.setTechnical(new TechnicalHeaderDef());
        header.setFunctional(new FunctionalHeaderDef());

        eventContent.setHeader(header);
        return eventContent;
    }
}

```

```
@Slf4j
@Named
@RequiredArgsConstructor
public class EnrichEventContentHeaderTransformationService {

    private static final String EVENT_TECH_ID_HEADER_KEY = "eventtechid";
    private static final String X_REQUEST_ID_HEADER_KEY = "x-request-id";
    private static final String ITR_ID_HEADER_KEY = "itrId";
    private static final String X_EVENT_VERSION_HEADER_KEY = "x-event-version";

    private static final String AP_CODE = "A100789";
    private static final String EVENT_NORM_VERSION = "2.4.1";
    private static final String EVENT_TYPE_VERSION = "1.0.0";
    private static final String ZONE_ID = ZoneId.of("Europe/Paris").getId();

    private static final String DEFAULT_ITR_ID = "ITRId_eventType_to_determine";
    private static final String DEFAULT_EVENT_TYPE_OE_ID = "OEId_eventType_to_determine";
    private static final String DEFAULT_MAIN_BUSINESS_OBJECT_OE_ID = "OEId_for_each_event";

    private static final DateTimeUtil DATE_TIME_UTIL = new DateTimeUtil();

    public void enrichEventContentHeader(EventFlowTransformationCtx transformationCtx) {
        EventContent eventContent = transformationCtx.getEventContent();
        EventContentHeader header = eventContent.getHeader();
        Map<String, List<String>> headers = transformationCtx.getHeaders();

        String mainBusinessObjectId = getMainBusinessObjectId(headers);
        String itrId = getItrId(headers);
        String eventVersion = getEventVersion(headers);

        enrichTechnicalHeader(header, headers, transformationCtx.getReceivedEventTimestamp());
        enrichFunctionalHeader(header, itrId, mainBusinessObjectId, eventVersion);
    }

    private void enrichTechnicalHeader(
            EventContentHeader header,
            Map<String, List<String>> headers,
            long receivedEventTimestamp
    ) {
        TechnicalHeaderDef technical = header.getTechnical();
        technical.setEventTechId(resolveEventTechId(headers));
        technical.setEventNormVersion(EVENT_NORM_VERSION);
        technical.setTimestamp(DATE_TIME_UTIL.convertEpochToIso8601(receivedEventTimestamp));
        technical.setTimeZone(ZONE_ID);
        technical.setApCode(AP_CODE);
    }

    private void enrichFunctionalHeader(
            EventContentHeader header,
            String itrId,
            String mainBusinessObjectId,
            String eventVersion
    ) {
        FunctionalHeaderDef functional = header.getFunctional();
        functional.setEventType(buildEventType(itrId, eventVersion));
        functional.setMainBusinessObject(buildMainBusinessObject(itrId, mainBusinessObjectId));
        functional.setSecondaryBusinessObjects(new ArrayList<>());
    }

    private String getMainBusinessObjectId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, X_REQUEST_ID_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("{} is empty", X_REQUEST_ID_HEADER_KEY);
                    return "";
                });
    }

    private String getItrId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, ITR_ID_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("ItrId not present in headers");
                    return DEFAULT_ITR_ID;
                });
    }

    private String getEventVersion(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, X_EVENT_VERSION_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("{} not present in headers, using default value [{}]",
                            X_EVENT_VERSION_HEADER_KEY, EVENT_TYPE_VERSION);
                    return EVENT_TYPE_VERSION;
                });
    }

    private String resolveEventTechId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, EVENT_TECH_ID_HEADER_KEY)
                .orElseGet(() -> {
                    String generatedEventTechId = UUID.randomUUID().toString();
                    log.warn(
                            "eventTechId not present in headers, generating eventTechId [{}]",
                            generatedEventTechId
                    );
                    return generatedEventTechId;
                });
    }

    private Optional<String> getFirstHeaderValue(Map<String, List<String>> headers, String key) {
        List<String> values = headers.get(key);
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(values.getFirst());
    }

    private EventTypeDef buildEventType(String itrId, String eventVersion) {
        EventTypeDef eventTypeDef = new EventTypeDef();
        eventTypeDef.setEventTypeId(TypeIdDef.builder()
                .OEId(DEFAULT_EVENT_TYPE_OE_ID)
                .ITRId(itrId)
                .build());
        eventTypeDef.setEventTypeVersion(eventVersion);
        return eventTypeDef;
    }

    private MainBusinessObjectDef buildMainBusinessObject(String itrId, String mainBusinessObjectId) {
        MainBusinessObjectDef mainBusinessObjectDef = new MainBusinessObjectDef();
        mainBusinessObjectDef.setId(mainBusinessObjectId);
        mainBusinessObjectDef.setType(TypeIdDef.builder()
                .ITRId(itrId)
                .OEId(DEFAULT_MAIN_BUSINESS_OBJECT_OE_ID)
                .build());
        return mainBusinessObjectDef;
    }
}

```


```
class EnrichEventContentHeaderTransformationServiceTest {

    private static final long RECEIVED_TIMESTAMP = 1713350400000L;

    private static final String EVENT_TECH_ID_HEADER_KEY = "eventtechid";
    private static final String X_REQUEST_ID_HEADER_KEY = "x-request-id";
    private static final String X_EVENT_VERSION_HEADER_KEY = "x-event-version";
    private static final String EVENT_VERSION = "2.3.4";
    private static final String ITR_ID_HEADER_KEY = "itrId";

    private static final String EVENT_TECH_ID = "event-tech-123";
    private static final String MAIN_BUSSINESS_OBJECT_ID = "main-bo-456";
    private static final String ITR_ID = "itr-789";

    private static final String DEFAULT_ITR_ID = "ITRId_eventType_to_determine";
    private static final String EVENT_NORM_VERSION = "2.4.1";
    private static final String EVENT_TYPE_VERSION = "1.0.0";
    private static final String AP_CODE = "A100789";
    private static final String TIME_ZONE = "Europe/Paris";

    private EnrichEventContentHeaderTransformationService service;

    @BeforeEach
    void setUp() {
        service = new EnrichEventContentHeaderTransformationService();
    }

    @Nested
    class NominalCase {

        @Test
        void should_enrich_all_headers_when_all_input_headers_are_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID),
                            X_EVENT_VERSION_HEADER_KEY, List.of(EVENT_VERSION)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertTechnicalHeader(header);
            assertFunctionalHeader(header, ITR_ID, MAIN_BUSSINESS_OBJECT_ID, EVENT_VERSION);
            assertThat(header.getTechnical().getEventTechId()).isEqualTo(EVENT_TECH_ID);
        }
    }

    @Nested
    class EventTechId {

        @Test
        void should_use_header_eventTechId_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getTechnical().getEventTechId())
                    .isEqualTo(EVENT_TECH_ID);
        }

        @Test
        void should_generate_eventTechId_when_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            String generatedEventTechId = ctx.getEventContent().getHeader().getTechnical().getEventTechId();

            assertThat(generatedEventTechId).isNotBlank();
            assertThatCode(() -> UUID.fromString(generatedEventTechId)).doesNotThrowAnyException();
        }

        @Test
        void should_generate_eventTechId_when_header_value_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            String generatedEventTechId = ctx.getEventContent().getHeader().getTechnical().getEventTechId();

            assertThat(generatedEventTechId).isNotBlank();
            assertThatCode(() -> UUID.fromString(generatedEventTechId)).doesNotThrowAnyException();
        }
    }

    @Nested
    class MainBusinessObjectId {

        @Test
        void should_set_mainBusinessObjectId_from_xRequestId_header() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getMainBusinessObject().getId())
                    .isEqualTo(MAIN_BUSSINESS_OBJECT_ID);
        }

        @Test
        void should_set_empty_mainBusinessObjectId_when_xRequestId_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getMainBusinessObject().getId())
                    .isEmpty();
        }

        @Test
        void should_set_empty_mainBusinessObjectId_when_xRequestId_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getMainBusinessObject().getId())
                    .isEmpty();
        }
    }

    @Nested
    class ItrId {

        @Test
        void should_set_itrId_from_header_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId()).isEqualTo(ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId()).isEqualTo(ITR_ID);
        }

        @Test
        void should_use_default_itrId_when_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
        }

        @Test
        void should_use_default_itrId_when_header_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of()
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                    .isEqualTo(DEFAULT_ITR_ID);
        }
    }

    @Nested
    class EventVersion {

        @Test
        void should_set_eventTypeVersion_from_header_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID),
                            X_EVENT_VERSION_HEADER_KEY, List.of(EVENT_VERSION)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getEventType().getEventTypeVersion())
                    .isEqualTo(EVENT_VERSION);
        }

        @Test
        void should_use_default_eventTypeVersion_when_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getEventType().getEventTypeVersion())
                    .isEqualTo(EVENT_TYPE_VERSION);
        }

        @Test
        void should_use_default_eventTypeVersion_when_header_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID),
                            X_EVENT_VERSION_HEADER_KEY, List.of()
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertThat(ctx.getEventContent().getHeader().getFunctional().getEventType().getEventTypeVersion())
                    .isEqualTo(EVENT_TYPE_VERSION);
        }
    }

    @Test
    void should_always_set_static_technical_and_functional_values() {
        EventFlowTransformationCtx ctx = buildContext(Map.of());

        service.enrichEventContentHeader(ctx);

        EventContentHeader header = ctx.getEventContent().getHeader();

        assertThat(header.getTechnical().getEventNormVersion()).isEqualTo(EVENT_NORM_VERSION);
        assertThat(header.getTechnical().getTimeZone()).isEqualTo(TIME_ZONE);
        assertThat(header.getTechnical().getApCode()).isEqualTo(AP_CODE);

        assertThat(header.getFunctional().getEventType().getEventTypeVersion()).isEqualTo(EVENT_TYPE_VERSION);
        assertThat(header.getFunctional().getEventType().getEventTypeId().getOEId())
                .isEqualTo("OEId_eventType_to_determine");
        assertThat(header.getFunctional().getMainBusinessObject().getType().getOEId())
                .isEqualTo("OEId_for_each_event");
        assertThat(header.getFunctional().getSecondaryBusinessObjects()).isNotNull().isEmpty();
    }

    private void assertTechnicalHeader(EventContentHeader header) {
        assertThat(header.getTechnical()).isNotNull();
        assertThat(header.getTechnical().getEventNormVersion()).isEqualTo(EVENT_NORM_VERSION);
        assertThat(header.getTechnical().getTimeZone()).isEqualTo(TIME_ZONE);
        assertThat(header.getTechnical().getApCode()).isEqualTo(AP_CODE);
        assertThat(header.getTechnical().getTimestamp()).isNotBlank();
    }

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
                .isEqualTo("OEId_eventType_to_determine");
        assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                .isEqualTo(expectedItrId);

        assertThat(header.getFunctional().getMainBusinessObject()).isNotNull();
        assertThat(header.getFunctional().getMainBusinessObject().getId()).isEqualTo(expectedMainBusinessObjectId);
        assertThat(header.getFunctional().getMainBusinessObject().getType().getOEId())
                .isEqualTo("OEId_for_each_event");
        assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId())
                .isEqualTo(expectedItrId);

        assertThat(header.getFunctional().getSecondaryBusinessObjects()).isNotNull().isEmpty();
    }

    private EventFlowTransformationCtx buildContext(Map<String, List<String>> headers) {
        EventFlowTransformationCtx ctx = new EventFlowTransformationCtx();
        ctx.setHeaders(headers);
        ctx.setReceivedEventTimestamp(RECEIVED_TIMESTAMP);
        ctx.setEventContent(buildEventContent());
        return ctx;
    }

    private EventContent buildEventContent() {
        EventContent eventContent = new EventContent();

        EventContentHeader header = new EventContentHeader();
        header.setTechnical(new TechnicalHeaderDef());
        header.setFunctional(new FunctionalHeaderDef());

        eventContent.setHeader(header);
        return eventContent;
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





