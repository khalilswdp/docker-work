```
class FlowConfigurationsLoaderTest {

    @Nested
    @DisplayName("loadAllFlowConfigurations")
    class LoadAllFlowConfigurationsTests {

        /**
         * If no file is present in the target folder, the loader must fail with
         * {@link ConfigurationLoadingException}.
         */
        @Test
        void should_throw_when_no_file_is_found_in_target_folder() {
            FlowConfigurationsLoader loader = new FlowConfigurationsLoader(
                    List.of("flow-config-loader/empty/"),
                    new ObjectMapper()
            );

            ConfigurationLoadingException exception = assertThrows(
                    ConfigurationLoadingException.class,
                    loader::loadAllFlowConfigurations
            );

            assertEquals("[GIL_003]An error occurred when trying to get the resources in the folder where the Flow Configurations should be stored ; [filesPath=flow-config-loader/empty/]", exception.getMessage());
        }

        /**
         * If two files resolve to the same flowId, the loader must reject them.
         */
        @Test
        void should_throw_when_two_flow_configurations_have_same_flow_id() {
            FlowConfigurationsLoader loader = new FlowConfigurationsLoader(
                    List.of("flow-config-loader/duplicate-flow-id/"),
                    new ObjectMapper()
            );

            ConfigurationLoadingException exception = assertThrows(
                    ConfigurationLoadingException.class,
                    loader::loadAllFlowConfigurations
            );

            assertEquals(
                    "[GIL_003]An error occurred when trying to get the resources in the folder where the Flow Configurations should be stored ; [filesPath=flow-config-loader/duplicate-flow-id/]",
                    exception.getMessage()
            );
        }

        /**
         * If one configuration file cannot be parsed, the current implementation
         * wraps that failure into a runtime exception from the private parsing method.
         */
        @Test
        void should_throw_runtime_exception_when_a_file_cannot_be_parsed() {
            FlowConfigurationsLoader loader = new FlowConfigurationsLoader(
                    List.of("flow-config-loader/malformed-json/"),
                    new ObjectMapper()
            );

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    loader::loadAllFlowConfigurations
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                    exception.getMessage().startsWith(
                            "[GIL_003]"
                    )
            );
        }
    }

    @Nested
    @DisplayName("FlowConfigurationMapper")
    class FlowConfigurationMapperTests {

        /**
         * Mapping a null DTO is invalid.
         */
        @Test
        void should_throw_when_flow_configuration_dto_is_null() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(null)
            );

            assertEquals("The FlowConfigurationDto passed for mapping is Null.", exception.getMessage());
        }

        /**
         * API DTOs should be mapped to API domain configurations.
         */
        @Test
        void should_map_api_flow_configuration_dto_to_domain() {
            AliasingTransformationDto requestAlias = new AliasingTransformationDto();
            requestAlias.setPointer("/request/path");

            AliasingTransformationDto responseAlias = new AliasingTransformationDto();
            responseAlias.setPointer("/response/path");

            ApiFlowConfigurationRequestTransformationDto requestDto =
                    new ApiFlowConfigurationRequestTransformationDto();
            requestDto.setAlias(List.of(requestAlias));

            ApiFlowConfigurationResponseTransformationDto responseDto =
                    new ApiFlowConfigurationResponseTransformationDto();
            responseDto.setAlias(List.of(responseAlias));

            ApiFlowConfigurationDto dto = new ApiFlowConfigurationDto();
            dto.setFlowId("api-flow");
            dto.setDirection("IN");
            dto.setAuthorizedCodeAp(List.of("all"));
            dto.setTargetWebApiCode("target-api");
            dto.setRequestTransformations(requestDto);
            dto.setResponseTransformations(responseDto);

            FlowConfiguration result = FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(dto);

            assertInstanceOf(ApiFlowConfiguration.class, result);

            ApiFlowConfiguration api = (ApiFlowConfiguration) result;
            assertEquals("api-flow", api.getFlowId());
            assertEquals(FlowDirection.IN, api.getDirection());
            assertEquals(List.of("all"), api.getAuthorizedCodeAp());
            assertEquals("target-api", api.getTargetWebApiCode());

            assertNotNull(api.getRequestTransformations());
            assertEquals(1, api.getRequestTransformations().getAlias().size());
            assertEquals("/request/path", api.getRequestTransformations().getAlias().get(0).getPointer());

            assertNotNull(api.getResponseTransformations());
            assertEquals(1, api.getResponseTransformations().getAlias().size());
            assertEquals("/response/path", api.getResponseTransformations().getAlias().get(0).getPointer());
        }

        /**
         * API DTOs must provide authorizedCodeAp.
         */
        @Test
        void should_throw_when_api_flow_authorized_code_ap_is_null() {
            ApiFlowConfigurationDto dto = new ApiFlowConfigurationDto();
            dto.setFlowId("api-flow");
            dto.setDirection("IN");
            dto.setAuthorizedCodeAp(null);

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(dto)
            );

            assertEquals("[GIL_002]The AuthorizedCodeAp passed for flow is api-flow Null.", exception.getMessage());
        }

        /**
         * Event DTOs should map avro, headers, aliases, target topic and itrId.
         */
        @Test
        void should_map_event_flow_configuration_dto_to_domain() {
            EventFlowConfigurationAvroDto avroDto = new EventFlowConfigurationAvroDto();
            avroDto.setPayloadType("payload-type");

            EventFlowConfigurationHeadersTransformationDto headersDto =
                    new EventFlowConfigurationHeadersTransformationDto();
            headersDto.setMainBusinessObjectId("id-path");
            headersDto.setMainBusinessObjectyType("type-path");
            headersDto.setBankCode("bank-path");

            AliasingTransformationDto aliasDto = new AliasingTransformationDto();
            aliasDto.setPointer("/event/path");

            EventFlowConfigurationTransformationDto transformationDto =
                    new EventFlowConfigurationTransformationDto();
            transformationDto.setAvro(avroDto);
            transformationDto.setContentHeaders(headersDto);
            transformationDto.setAlias(List.of(aliasDto));

            EventFlowConfigurationDto dto = new EventFlowConfigurationDto();
            dto.setFlowId("event-flow");
            dto.setDirection("OUT");
            dto.setAuthorizedCodeAp(List.of("ap12345"));
            dto.setTargetTopic("topic-1");
            dto.setItrId("itr-1");
            dto.setTransformationParams(transformationDto);

            FlowConfiguration result = FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(dto);

            assertInstanceOf(EventFlowConfiguration.class, result);

            EventFlowConfiguration event = (EventFlowConfiguration) result;
            assertEquals("event-flow", event.getFlowId());
            assertEquals(FlowDirection.OUT, event.getDirection());
            assertEquals(List.of("ap12345"), event.getAuthorizedCodeAp());
            assertEquals("topic-1", event.getTargetTopic());
            assertEquals("itr-1", event.getItrId());

            assertNotNull(event.getTransformations());
            assertNotNull(event.getTransformations().getAvro());
            assertEquals("payload-type", event.getTransformations().getAvro().getPayloadType());

            assertNotNull(event.getTransformations().getHeaders());
            assertEquals("id-path", event.getTransformations().getHeaders().getMainBusinessObjectId());
            assertEquals("type-path", event.getTransformations().getHeaders().getMainBusinessObjectType());
            assertEquals("bank-path", event.getTransformations().getHeaders().getBankCode());

            assertEquals(1, event.getTransformations().getAlias().size());
            assertEquals("/event/path", event.getTransformations().getAlias().get(0).getPointer());
        }

        /**
         * Event DTOs must provide authorizedCodeAp.
         */
        @Test
        void should_throw_when_event_flow_authorized_code_ap_is_empty() {
            EventFlowConfigurationDto dto = new EventFlowConfigurationDto();
            dto.setFlowId("event-flow");
            dto.setDirection("OUT");
            dto.setAuthorizedCodeAp(List.of());

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(dto)
            );

            assertEquals("[GIL_002]The AuthorizedCodeAp passed for flow is event-flow Null.", exception.getMessage());
        }

        /**
         * File DTOs should map target bucket and common base fields.
         */
        @Test
        void should_map_file_flow_configuration_dto_to_domain() {
            FileFlowConfigurationDto dto = new FileFlowConfigurationDto();
            dto.setFlowId("file-flow");
            dto.setDirection("IN");
            dto.setTargetBucket("bucket-1");

            FlowConfiguration result = FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(dto);

            assertInstanceOf(FileFlowConfiguration.class, result);

            FileFlowConfiguration file = (FileFlowConfiguration) result;
            assertEquals("file-flow", file.getFlowId());
            assertEquals(FlowDirection.IN, file.getDirection());
            assertEquals("bucket-1", file.getTargetBucket());
        }

        /**
         * Null API alias lists should be normalized to empty lists.
         */
        @Test
        void should_map_null_api_alias_lists_to_empty_lists() {
            ApiFlowConfigurationRequestTransformationDto requestDto =
                    new ApiFlowConfigurationRequestTransformationDto();
            requestDto.setAlias(null);

            ApiFlowConfigurationResponseTransformationDto responseDto =
                    new ApiFlowConfigurationResponseTransformationDto();
            responseDto.setAlias(null);

            ApiFlowConfigurationDto dto = new ApiFlowConfigurationDto();
            dto.setFlowId("api-flow");
            dto.setDirection("IN");
            dto.setAuthorizedCodeAp(List.of("all"));
            dto.setRequestTransformations(requestDto);
            dto.setResponseTransformations(responseDto);

            ApiFlowConfiguration result =
                    (ApiFlowConfiguration) FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(dto);

            assertNotNull(result.getRequestTransformations());
            assertNotNull(result.getRequestTransformations().getAlias());
            assertEquals(0, result.getRequestTransformations().getAlias().size());

            assertNotNull(result.getResponseTransformations());
            assertNotNull(result.getResponseTransformations().getAlias());
            assertEquals(0, result.getResponseTransformations().getAlias().size());
        }

        /**
         * Null event alias lists should also be normalized to empty lists.
         */
        @Test
        void should_map_null_event_alias_list_to_empty_list() {
            EventFlowConfigurationTransformationDto transformationDto =
                    new EventFlowConfigurationTransformationDto();
            transformationDto.setAlias(null);
            transformationDto.setAvro(null);
            transformationDto.setContentHeaders(null);

            EventFlowConfigurationDto dto = new EventFlowConfigurationDto();
            dto.setFlowId("event-flow");
            dto.setDirection("OUT");
            dto.setAuthorizedCodeAp(List.of("ap12345"));
            dto.setTransformationParams(transformationDto);

            EventFlowConfiguration result =
                    (EventFlowConfiguration) FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(dto);

            assertNotNull(result.getTransformations());
            assertNotNull(result.getTransformations().getAlias());
            assertEquals(0, result.getTransformations().getAlias().size());
            assertNull(result.getTransformations().getAvro());
            assertNull(result.getTransformations().getHeaders());
        }

        /**
         * Unsupported DTO subtypes must be rejected explicitly.
         */
        @Test
        void should_throw_when_flow_configuration_dto_subtype_is_not_managed() {
            FlowConfigurationDto unsupportedDto = new FlowConfigurationDto() {
            };
            unsupportedDto.setFlowId("unsupported");
            unsupportedDto.setDirection("IN");

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationsLoader.FlowConfigurationMapper.toDomain(unsupportedDto)
            );

            assertEquals(
                    "[GIL_002]The Subtype of the FlowConfigurationDto is not managed by the mapping function.",
                    exception.getMessage()
            );
        }
    }
}

```

```

class FlowConfigurationUtilTest {

    @Nested
    @DisplayName("ApiFlowConfiguration authorizedCodeAp validation")
    class ApiFlowAuthorizedCodeApTests {

        /**
         * An API flow configuration must reject a null authorizedCodeAp list.
         */
        @Test
        void should_throw_when_api_flow_authorized_code_ap_is_null() {
            ApiFlowConfiguration configuration = ApiFlowConfiguration.builder()
                    .flowId("api01")
                    .direction(FlowDirection.IN)
                    .authorizedCodeAp(null)
                    .targetWebApiCode("target-api")
                    .build();

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationUtil.checkFlowConfiguration(configuration)
            );

            assertEquals(
                    "[GIL_002]The AuthorizedCodeApi passed for flow api01is Null.",
                    exception.getMessage()
            );
        }

        /**
         * An API flow configuration must reject an empty authorizedCodeAp list.
         */
        @Test
        void should_throw_when_api_flow_authorized_code_ap_is_empty() {
            ApiFlowConfiguration configuration = ApiFlowConfiguration.builder()
                    .flowId("api01")
                    .direction(FlowDirection.IN)
                    .authorizedCodeAp(List.of())
                    .targetWebApiCode("target-api")
                    .build();

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationUtil.checkFlowConfiguration(configuration)
            );

            assertEquals(
                    "[GIL_002]The AuthorizedCodeApi passed for flow api01is Null.",
                    exception.getMessage()
            );
        }

        /**
         * An API flow configuration should pass this specific validation when
         * authorizedCodeAp is present and non-empty.
         */
        @Test
        void should_not_throw_when_api_flow_authorized_code_ap_is_present() {
            ApiFlowConfiguration configuration = ApiFlowConfiguration.builder()
                    .flowId("api01")
                    .direction(FlowDirection.IN)
                    .authorizedCodeAp(List.of("all"))
                    .targetWebApiCode("target-api")
                    .build();

            assertDoesNotThrow(() -> FlowConfigurationUtil.checkFlowConfiguration(configuration));
        }
    }

    @Nested
    @DisplayName("EventFlowConfiguration authorizedCodeAp validation")
    class EventFlowAuthorizedCodeApTests {

        /**
         * An event flow configuration must reject a null authorizedCodeAp list.
         */
        @Test
        void should_throw_when_event_flow_authorized_code_ap_is_null() {
            EventFlowConfiguration configuration = EventFlowConfiguration.builder()
                    .flowId("event01")
                    .direction(FlowDirection.OUT)
                    .authorizedCodeAp(null)
                    .targetTopic("topic-1")
                    .itrId("itr-1")
                    .transformations(validEventTransformations())
                    .build();

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationUtil.checkFlowConfiguration(configuration)
            );

            assertEquals(
                    "[GIL_002]The AuthorizedCodeAp passed for mapping is Null.",
                    exception.getMessage()
            );
        }

        /**
         * An event flow configuration must reject an empty authorizedCodeAp list.
         */
        @Test
        void should_throw_when_event_flow_authorized_code_ap_is_empty() {
            EventFlowConfiguration configuration = EventFlowConfiguration.builder()
                    .flowId("event01")
                    .direction(FlowDirection.OUT)
                    .authorizedCodeAp(List.of())
                    .targetTopic("topic-1")
                    .itrId("itr-1")
                    .transformations(validEventTransformations())
                    .build();

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationUtil.checkFlowConfiguration(configuration)
            );

            assertEquals(
                    "[GIL_002]The AuthorizedCodeAp passed for mapping is Null.",
                    exception.getMessage()
            );
        }

        /**
         * An event flow configuration should pass this specific validation when
         * authorizedCodeAp is present and non-empty.
         */
        @Test
        void should_not_throw_when_event_flow_authorized_code_ap_is_present() {
            EventFlowConfiguration configuration = EventFlowConfiguration.builder()
                    .flowId("event01")
                    .direction(FlowDirection.OUT)
                    .authorizedCodeAp(List.of("ap12345"))
                    .targetTopic("topic-1")
                    .itrId("itr-1")
                    .transformations(validEventTransformations())
                    .build();

            assertDoesNotThrow(() -> FlowConfigurationUtil.checkFlowConfiguration(configuration));
        }
    }

    @Nested
    @DisplayName("Interaction with other validation rules")
    class AuthorizedCodeApOrderAndIsolationTests {

        /**
         * For API flows, when authorizedCodeAp is valid, other invalid fields should
         * still be validated afterward.
         *
         * <p>This ensures the newly added authorizedCodeAp check does not prevent
         * the rest of the validation logic from running in valid cases.
         */
        @Test
        void should_continue_api_validation_after_authorized_code_ap_check() {
            ApiFlowConfiguration configuration = ApiFlowConfiguration.builder()
                    .flowId("api01")
                    .direction(FlowDirection.IN)
                    .authorizedCodeAp(List.of("all"))
                    .targetWebApiCode("bad space value")
                    .build();

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationUtil.checkFlowConfiguration(configuration)
            );

            assertEquals(
                    "[GIL_002]the target web api code field does not responect the excpected constraints for a FileFlowConfiguration object",
                    exception.getMessage()
            );
        }

        /**
         * For event flows, when authorizedCodeAp is valid, later validations should
         * still run and fail on invalid targetTopic if necessary.
         */
        @Test
        void should_continue_event_validation_after_authorized_code_ap_check() {
            EventFlowConfiguration configuration = EventFlowConfiguration.builder()
                    .flowId("event01")
                    .direction(FlowDirection.OUT)
                    .authorizedCodeAp(List.of("ap12345"))
                    .targetTopic("bad topic with spaces")
                    .itrId("itr-1")
                    .transformations(validEventTransformations())
                    .build();

            ConfigurationMalformedException exception = assertThrows(
                    ConfigurationMalformedException.class,
                    () -> FlowConfigurationUtil.checkFlowConfiguration(configuration)
            );

            assertEquals(
                    "[GIL_002]The target Topic field does not respect the expected contraint for an eventFlowCofiguration object",
                    exception.getMessage()
            );
        }
    }

    /**
     * Builds a minimal valid event transformation block so tests can focus on
     * authorizedCodeAp validation without failing earlier on unrelated fields.
     */
    private EventFlowConfigurationTransformationConfiguration validEventTransformations() {
        return EventFlowConfigurationTransformationConfiguration.builder()
                .avro(new EventFlowConfigurationAvro("payload-type"))
                .headers(EventFlowConfigurationHeaders.builder()
                        .mainBusinessObjectId("mbid")
                        .mainBusinessObjectType("mbtype")
                        .bankCode("bank01")
                        .build())
                .alias(List.of(
                        AliasingTransformationConfiguration.builder()
                                .pointer("/payload/path")
                                .build()
                ))
                .build();
    }
}

```



