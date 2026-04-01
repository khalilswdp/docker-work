```

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BcefApiGatewayAdapterTest {

    private FromBnppToPartnerUserCase fromBnppToPartnerUserCase;
    private BcefApiGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        fromBnppToPartnerUserCase = mock(FromBnppToPartnerUserCase.class);
        adapter = new BcefApiGatewayAdapter(fromBnppToPartnerUserCase);
    }

    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/estreem/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("a123456")
                .subject(null)
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
        }).when(fromBnppToPartnerUserCase).doPipeline(any(ApiFlow.class));

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromBnppToPartnerUserCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    @Test
    void adapter_shouldThrowException_whenIssuerIsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        TokenContext tokenContext = TokenContext.builder()
                .issuer(null)
                .subject(null)
                .build();

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> adapter.adapter("flow123", request, "body", headers)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            verify(fromBnppToPartnerUserCase, never()).doPipeline(any());
        }
    }

    @Test
    void adapter_shouldThrowException_whenSubjectIsPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        TokenContext tokenContext = TokenContext.builder()
                .issuer("a123456")
                .subject("unexpected-subject")
                .build();

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> adapter.adapter("flow123", request, "body", headers)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            verify(fromBnppToPartnerUserCase, never()).doPipeline(any());
        }
    }

    @Test
    void adapter_shouldThrowException_whenIssuerDoesNotMatchRegex() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        TokenContext tokenContext = TokenContext.builder()
                .issuer("invalidIssuer")
                .subject(null)
                .build();

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> adapter.adapter("flow123", request, "body", headers)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            verify(fromBnppToPartnerUserCase, never()).doPipeline(any());
        }
    }
}


package com.example;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BcefApiGatewayAdapterTest {

    private FromBnppToPartnerUserCase fromBnppToPartnerUserCase;
    private BcefApiGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        fromBnppToPartnerUserCase = mock(FromBnppToPartnerUserCase.class);
        adapter = new BcefApiGatewayAdapter(fromBnppToPartnerUserCase);
    }

    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/estreem/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("a123456")
                .subject(null)
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
        }).when(fromBnppToPartnerUserCase).doPipeline(any(ApiFlow.class));

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromBnppToPartnerUserCase, times(1)).doPipeline(any(ApiFlow.class));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidTokenContexts")
    void adapter_shouldThrowException_whenTokenIsInvalid(TokenContext tokenContext) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> adapter.adapter("flow123", request, "body", headers)
            );

            assertEquals(GilErrorCode.INVALID_TOKEN, exception.getCode());
            verify(fromBnppToPartnerUserCase, never()).doPipeline(any());
        }
    }

    private static Stream<TokenContext> invalidTokenContexts() {
        return Stream.of(
                TokenContext.builder()
                        .issuer(null)
                        .subject(null)
                        .build(),
                TokenContext.builder()
                        .issuer("a123456")
                        .subject("unexpected-subject")
                        .build(),
                TokenContext.builder()
                        .issuer("invalidIssuer")
                        .subject(null)
                        .build()
        );
    }
}


package com.example;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BcefApiGatewayAdapterTest {

    private FromBnppToPartnerUserCase fromBnppToPartnerUserCase;
    private BcefApiGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        fromBnppToPartnerUserCase = mock(FromBnppToPartnerUserCase.class);
        adapter = new BcefApiGatewayAdapter(fromBnppToPartnerUserCase);
    }

    @Test
    void adapter_shouldProcessRequest_whenTokenIsValid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/estreem/v1/api/flow123/resource");
        when(request.getQueryString()).thenReturn("a=1");

        TokenContext tokenContext = TokenContext.builder()
                .issuer("a123456")
                .subject(null)
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
        }).when(fromBnppToPartnerUserCase).doPipeline(any(ApiFlow.class));

        try (MockedStatic<JwtTokenUtil> jwtMock = mockStatic(JwtTokenUtil.class)) {
            jwtMock.when(() -> JwtTokenUtil.getTokenContext(request)).thenReturn(tokenContext);

            ResponseEntity<String> response = adapter.adapter("flow123", request, "body", headers);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("{\"ok\":true}", response.getBody());
            verify(fromBnppToPartnerUserCase, times(1)).doPipeline(any(ApiFlow.class));
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
            verify(fromBnppToPartnerUserCase, never()).doPipeline(any());
        }
    }

    private static Stream<Arguments> invalidTokenContexts() {
        return Stream.of(
                Arguments.of("issuer is null",
                        TokenContext.builder().issuer(null).subject(null).build()),
                Arguments.of("subject is present",
                        TokenContext.builder().issuer("a123456").subject("unexpected-subject").build()),
                Arguments.of("issuer does not match regex",
                        TokenContext.builder().issuer("invalidIssuer").subject(null).build())
        );
    }
}

```



