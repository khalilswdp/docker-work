```

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.util.*;

/**
 * Service responsible for enriching the {@link EventContentHeader} of an event
 * from transport-level headers carried in the {@link EventFlowTransformationCtx}.
 *
 * <p>The enrichment is split into two parts:
 * <ul>
 *     <li><b>Technical header</b>: metadata such as event technical id, norm version,
 *     timestamp, timezone and application code.</li>
 *     <li><b>Functional header</b>: business-oriented metadata such as event type,
 *     main business object and secondary business objects.</li>
 * </ul>
 *
 * <p>When some input headers are missing or blank, default values are applied:
 * <ul>
 *     <li>{@code eventtechid}: generated as a random UUID</li>
 *     <li>{@code itrId}: falls back to a predefined default ITR id</li>
 *     <li>{@code x-event-version}: falls back to the default event type version</li>
 *     <li>{@code x-request-id}: falls back to an empty main business object id</li>
 * </ul>
 */
@Slf4j
@Named
@RequiredArgsConstructor
public class EnrichEventContentHeaderTransformationService {

    /** Header name containing the technical event id. */
    private static final String EVENT_TECH_ID_HEADER_KEY = "eventtechid";

    /** Header name containing the request id, reused as main business object id. */
    private static final String X_REQUEST_ID_HEADER_KEY = "x-request-id";

    /** Header name containing the ITR identifier. */
    private static final String ITR_ID_HEADER_KEY = "itrId";

    /** Header name containing the functional event version. */
    private static final String X_EVENT_VERSION_HEADER_KEY = "x-event-version";

    /** Static application code injected into the technical header. */
    private static final String AP_CODE = "A100789";

    /** Supported event norm version injected into the technical header. */
    private static final String EVENT_NORM_VERSION = "2.4.1";

    /** Default event type version used when no version header is provided. */
    private static final String EVENT_TYPE_VERSION = "eventVersion-tobe-defined";

    /** Time zone associated with the generated event timestamp. */
    private static final String ZONE_ID = ZoneId.of("Europe/Paris").getId();

    /** Default ITR id used when the corresponding header is missing or blank. */
    private static final String DEFAULT_ITR_ID = "ITRId_eventType_to_determine";

    /** Default OE id used for the event type block. */
    private static final String DEFAULT_EVENT_TYPE_OE_ID = "OEId_eventType_to_determine";

    /** Default OE id used for the main business object block. */
    private static final String DEFAULT_MAIN_BUSINESS_OBJECT_OE_ID = "OEId_for_each_event";

    /** Utility used to convert epoch timestamps into ISO-8601 strings. */
    private static final DateTimeUtil DATE_TIME_UTIL = new DateTimeUtil();

    /**
     * Enriches the event content header from the transformation context.
     *
     * <p>This method orchestrates the whole enrichment flow by:
     * <ol>
     *     <li>reading raw input headers from the context,</li>
     *     <li>resolving business values and defaults,</li>
     *     <li>populating the technical and functional sections of the event header.</li>
     * </ol>
     *
     * @param transformationCtx the transformation context containing the target event content,
     *                          received timestamp and transport headers
     */
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

    /**
     * Populates the technical part of the event header.
     *
     * <p>The following fields are set:
     * <ul>
     *     <li>event technical id</li>
     *     <li>event norm version</li>
     *     <li>event timestamp</li>
     *     <li>timezone</li>
     *     <li>application code</li>
     * </ul>
     *
     * @param header the event header to enrich
     * @param headers the incoming transport headers
     * @param receivedEventTimestamp the raw event reception timestamp in epoch milliseconds
     */
    private void enrichTechnicalHeader(
            EventContentHeader header,
            Map<String, List<String>> headers,
            long receivedEventTimestamp
    ) {
        TechnicalHeaderDef technical = header.getTechnical();

        // Use the incoming event tech id when available, otherwise generate one.
        technical.setEventTechId(resolveEventTechId(headers));

        // Static metadata required by the target norm.
        technical.setEventNormVersion(EVENT_NORM_VERSION);
        technical.setTimestamp(DATE_TIME_UTIL.convertEpochToIso8601(receivedEventTimestamp));
        technical.setTimeZone(ZONE_ID);
        technical.setApCode(AP_CODE);
    }

    /**
     * Populates the functional part of the event header.
     *
     * <p>The following blocks are created:
     * <ul>
     *     <li>event type</li>
     *     <li>main business object</li>
     *     <li>secondary business objects, initialized as an empty list</li>
     * </ul>
     *
     * @param header the event header to enrich
     * @param itrId the resolved ITR id
     * @param mainBusinessObjectId the resolved main business object id
     * @param eventVersion the resolved event version
     */
    private void enrichFunctionalHeader(
            EventContentHeader header,
            String itrId,
            String mainBusinessObjectId,
            String eventVersion
    ) {
        FunctionalHeaderDef functional = header.getFunctional();

        functional.setEventType(buildEventType(itrId, eventVersion));
        functional.setMainBusinessObject(buildMainBusinessObject(itrId, mainBusinessObjectId));

        // The target model requires the collection to be initialized even when empty.
        functional.setSecondaryBusinessObjects(new ArrayList<>());
    }

    /**
     * Resolves the main business object id from the {@code x-request-id} header.
     *
     * <p>If the header is missing, empty or blank, an empty string is returned.
     *
     * @param headers incoming transport headers
     * @return the resolved main business object id, or an empty string if unavailable
     */
    private String getMainBusinessObjectId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, X_REQUEST_ID_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("{} is empty", X_REQUEST_ID_HEADER_KEY);
                    return "";
                });
    }

    /**
     * Resolves the ITR id from the {@code itrId} header.
     *
     * <p>If the header is missing, empty or blank, a default ITR id is used.
     *
     * @param headers incoming transport headers
     * @return the resolved ITR id, or the default value if unavailable
     */
    private String getItrId(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, ITR_ID_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("ItrId not present in headers");
                    return DEFAULT_ITR_ID;
                });
    }

    /**
     * Resolves the event version from the {@code x-event-version} header.
     *
     * <p>If the header is missing, empty or blank, the default event type version is used.
     *
     * @param headers incoming transport headers
     * @return the resolved event version, or the default version if unavailable
     */
    private String getEventVersion(Map<String, List<String>> headers) {
        return getFirstHeaderValue(headers, X_EVENT_VERSION_HEADER_KEY)
                .orElseGet(() -> {
                    log.warn("{} not present in headers, using default value [{}]",
                            X_EVENT_VERSION_HEADER_KEY, EVENT_TYPE_VERSION);
                    return EVENT_TYPE_VERSION;
                });
    }

    /**
     * Resolves the technical event id from the {@code eventtechid} header.
     *
     * <p>If the header is missing, empty or blank, a random UUID is generated.
     *
     * @param headers incoming transport headers
     * @return the resolved or generated technical event id
     */
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

    /**
     * Returns the first non-blank value associated with the given header key.
     *
     * <p>A value is considered absent when:
     * <ul>
     *     <li>the header does not exist,</li>
     *     <li>the list is empty,</li>
     *     <li>the first value is {@code null},</li>
     *     <li>the first value is blank.</li>
     * </ul>
     *
     * @param headers incoming transport headers
     * @param key the header key to read
     * @return an {@link Optional} containing the first usable value if present
     */
    private Optional<String> getFirstHeaderValue(Map<String, List<String>> headers, String key) {
        List<String> values = headers.get(key);
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(values.getFirst());
    }

    /**
     * Builds the functional event type block.
     *
     * @param itrId the ITR id to assign to the event type identifier
     * @param eventVersion the event type version to assign
     * @return a fully initialized {@link EventTypeDef}
     */
    private EventTypeDef buildEventType(String itrId, String eventVersion) {
        EventTypeDef eventTypeDef = new EventTypeDef();
        eventTypeDef.setEventTypeId(TypeIdDef.builder()
                .OEId(DEFAULT_EVENT_TYPE_OE_ID)
                .ITRId(itrId)
                .build());
        eventTypeDef.setEventTypeVersion(eventVersion);
        return eventTypeDef;
    }

    /**
     * Builds the functional main business object block.
     *
     * @param itrId the ITR id to assign to the business object type
     * @param mainBusinessObjectId the business object id
     * @return a fully initialized {@link MainBusinessObjectDef}
     */
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import org.junit.jupiter.api.Nested;

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
    private static final String HEADER_EVENT_VERSION = "eventVersion-from-header";
    private static final String EVENT_VERSION = "eventVersion-tobe-defined";
    private static final String ITR_ID_HEADER_KEY = "itrId";

    private static final String EVENT_TECH_ID = "event-tech-123";
    private static final String MAIN_BUSINESS_OBJECT_ID = "main-bo-456";
    private static final String ITR_ID = "itr-789";

    private static final String DEFAULT_ITR_ID = "ITRId_eventType_to_determine";
    private static final String EVENT_NORM_VERSION = "2.4.1";
    private static final String AP_CODE = "A100789";
    private static final String TIME_ZONE = "Europe/Paris";

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
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID),
                            X_EVENT_VERSION_HEADER_KEY, List.of(HEADER_EVENT_VERSION)
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
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
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
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            String generatedEventTechId = ctx.getEventContent().getHeader().getTechnical().getEventTechId();

            // Generated ids must be non-blank and valid UUIDs.
            assertThat(generatedEventTechId).isNotBlank();

            assertThatCode(() -> {
                UUID uuid = UUID.fromString(generatedEventTechId);
                assertThat(uuid).isNotNull();
            }).doesNotThrowAnyException();

        }

        @Test
        void should_generate_eventTechId_when_header_value_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            String generatedEventTechId = ctx.getEventContent().getHeader().getTechnical().getEventTechId();

            assertThat(generatedEventTechId).isNotBlank();
            assertThatCode(() -> {
                UUID uuid = UUID.fromString(generatedEventTechId);
                assertThat(uuid).isNotNull();
            }).doesNotThrowAnyException();

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
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(ctx.getEventContent().getHeader(), ITR_ID, MAIN_BUSINESS_OBJECT_ID, EVENT_VERSION);
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

            assertFunctionalHeader(ctx.getEventContent().getHeader(), ITR_ID, "", EVENT_VERSION);
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

            assertFunctionalHeader(ctx.getEventContent().getHeader(), ITR_ID, "", EVENT_VERSION);
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
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            EventContentHeader header = ctx.getEventContent().getHeader();

            // The resolved ITR id must be propagated consistently to both blocks.
            assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId()).isEqualTo(ITR_ID);
            assertThat(header.getFunctional().getMainBusinessObject().getType().getITRId()).isEqualTo(ITR_ID);
        }

        @Test
        void should_use_default_itrId_when_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID)
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
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
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

    /**
     * Tests extraction and fallback behavior for the functional event version.
     */
    @Nested
    class EventVersion {

        @Test
        void should_set_eventTypeVersion_from_header_when_present() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID),
                            X_EVENT_VERSION_HEADER_KEY, List.of(HEADER_EVENT_VERSION)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(ctx.getEventContent().getHeader(), ITR_ID, MAIN_BUSINESS_OBJECT_ID, HEADER_EVENT_VERSION);
        }

        @Test
        void should_use_default_eventTypeVersion_when_header_is_missing() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID)
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(
                    ctx.getEventContent().getHeader(),
                    ITR_ID,
                    MAIN_BUSINESS_OBJECT_ID,
                    EVENT_VERSION
            );
        }

        @Test
        void should_use_default_eventTypeVersion_when_header_list_is_empty() {
            EventFlowTransformationCtx ctx = buildContext(
                    Map.of(
                            EVENT_TECH_ID_HEADER_KEY, List.of(EVENT_TECH_ID),
                            X_REQUEST_ID_HEADER_KEY, List.of(MAIN_BUSINESS_OBJECT_ID),
                            ITR_ID_HEADER_KEY, List.of(ITR_ID),
                            X_EVENT_VERSION_HEADER_KEY, List.of()
                    )
            );

            service.enrichEventContentHeader(ctx);

            assertFunctionalHeader(
                    ctx.getEventContent().getHeader(),
                    ITR_ID,
                    MAIN_BUSINESS_OBJECT_ID,
                    EVENT_VERSION
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

        assertFunctionalHeader(header, DEFAULT_ITR_ID, "", EVENT_VERSION);
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
                .isEqualTo("OEId_eventType_to_determine");
        assertThat(header.getFunctional().getEventType().getEventTypeId().getITRId())
                .isEqualTo(expectedItrId);

        assertThat(header.getFunctional().getMainBusinessObject()).isNotNull();
        assertThat(header.getFunctional().getMainBusinessObject().getId()).isEqualTo(expectedMainBusinessObjectId);
        assertThat(header.getFunctional().getMainBusinessObject().getType().getOEId())
                .isEqualTo("OEId_for_each_event");
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





