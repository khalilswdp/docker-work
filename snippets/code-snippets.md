```

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EstreemApiGatewayAdapterTest {

    private FromPartnerToBnppUseCase fromPartnerToBnppUseCase;
    private EstreemApiGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        fromPartnerToBnppUseCase = mock(FromPartnerToBnppUseCase.class);
        adapter = new EstreemApiGatewayAdapter(fromPartnerToBnppUseCase);
    }

    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/bcef/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("a123456")
                .subject("ap12345")
                .build();

        doAnswer(invocation -> {
            ApiFlow apiFlow = invocation.getArgument(0);
            apiFlow.setResponse(
                    ApiFlowResponse.builder()
                            .statusCode(200)
                            .responseHeaders(Map.of("Content-Type", List.of("application/json")))
                            .responsePayload("{\"ok\":true}")
                            .build()
            );
            return null;
        }).when(fromPartnerToBnppUseCase).doPipeline(any(ApiFlow.class));

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromPartnerToBnppUseCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("invalidTokenContexts")
    void adapter_shouldThrowException_whenTokenIsInvalid(String ignoredCaseName, TokenContext tokenContext) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> adapter.adapter("flow123", request, "body", headers)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            verify(fromPartnerToBnppUseCase, never()).doPipeline(any());
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> invalidTokenContexts() {
        return Stream.of(
                Arguments.of(
                        "issuer is null",
                        TokenContext.builder()
                                .issuer(null)
                                .subject("p123456")
                                .build()
                ),
                Arguments.of(
                        "subject is null",
                        TokenContext.builder()
                                .issuer("a123456")
                                .subject(null)
                                .build()
                ),
                Arguments.of(
                        "issuer does not match regex",
                        TokenContext.builder()
                                .issuer("invalidIssuer")
                                .subject("p123456")
                                .build()
                ),
                Arguments.of(
                        "subject does not match regex",
                        TokenContext.builder()
                                .issuer("a123456")
                                .subject("invalidSubject")
                                .build()
                ),
                Arguments.of(
                        "issuer and subject do not match regex",
                        TokenContext.builder()
                                .issuer("badIssuer")
                                .subject("badSubject")
                                .build()
                )
        );
    }
}

```



