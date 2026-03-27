```
/**
 * Unit tests for {@link JwtTokenUtil}.
 *
 * <p>This suite validates:
 * <ul>
 *     <li>utility-class constructor protection</li>
 *     <li>bearer token extraction rules</li>
 *     <li>JWT generation</li>
 *     <li>JWT parsing and validation</li>
 *     <li>high-level token extraction from an HTTP request</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenUtilTest {

    @Mock
    private HttpServletRequest httpServletRequest;

    /**
     * Tests related to the utility-class design.
     */
    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        /**
         * The utility class must not be instantiable.
         */
        @Test
        void should_throw_when_instantiating_utility_class() throws Exception {
            var constructor = JwtTokenUtil.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            Exception exception = assertThrows(Exception.class, constructor::newInstance);

            // Reflection wraps the original exception, so we assert on the cause.
            assertInstanceOf(IllegalStateException.class, exception.getCause());
            assertEquals("Utility class", exception.getCause().getMessage());
        }
    }

    /**
     * Tests related to raw bearer token extraction from the Authorization header.
     */
    @Nested
    @DisplayName("extractBearerToken")
    class ExtractBearerTokenTests {

        /**
         * A missing Authorization header must be rejected.
         */
        @Test
        void should_throw_when_authorization_header_is_missing() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn(null);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> JwtTokenUtil.extractBearerToken(httpServletRequest)
            );

            assertEquals("Authorization header is missing", exception.getMessage());
        }

        /**
         * A blank Authorization header must be rejected.
         */
        @Test
        void should_throw_when_authorization_header_is_blank() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn("   ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> JwtTokenUtil.extractBearerToken(httpServletRequest)
            );

            assertEquals("Authorization header is missing", exception.getMessage());
        }

        /**
         * The header must start with the Bearer prefix.
         */
        @Test
        void should_throw_when_authorization_header_does_not_start_with_bearer() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn("Basic abc.def");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> JwtTokenUtil.extractBearerToken(httpServletRequest)
            );

            assertEquals("Authorization header must start with Bearer", exception.getMessage());
        }

        /**
         * A Bearer header without an actual token must be rejected.
         */
        @Test
        void should_throw_when_bearer_token_is_missing() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer   ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> JwtTokenUtil.extractBearerToken(httpServletRequest)
            );

            assertEquals("Bearer token is missing", exception.getMessage());
        }

        /**
         * A valid Bearer header should return the token payload as-is.
         */
        @Test
        void should_extract_bearer_token() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer abc.def.ghi");

            String token = JwtTokenUtil.extractBearerToken(httpServletRequest);

            assertEquals("abc.def.ghi", token);
        }

        /**
         * If a token contains exactly one dot and does not end with a dot,
         * the utility appends the missing trailing dot.
         *
         * <p>This documents the current normalization behavior.
         */
        @Test
        void should_append_trailing_dot_when_token_contains_exactly_one_dot() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer abc.def");

            String token = JwtTokenUtil.extractBearerToken(httpServletRequest);

            assertEquals("abc.def.", token);
        }

        /**
         * If the token already ends with a dot, it must not be modified.
         */
        @Test
        void should_not_append_dot_when_token_already_ends_with_dot() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer abc.def.");

            String token = JwtTokenUtil.extractBearerToken(httpServletRequest);

            assertEquals("abc.def.", token);
        }
    }

    /**
     * Tests related to JWT string generation.
     */
    @Nested
    @DisplayName("generateToken")
    class GenerateTokenTests {

        /**
         * Token generation requires a non-null token context.
         */
        @Test
        void should_throw_when_token_context_is_null() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> JwtTokenUtil.generateToken(null)
            );

            assertEquals("TokenContext is null", exception.getMessage());
        }

        /**
         * A valid token context should be serialized into a JWT string.
         */
        @Test
        void should_generate_token_from_token_context() {
            TokenContext tokenContext = TokenContext.builder()
                    .issuer("ap12345")
                    .subject("ap54321")
                    .build();

            String token = JwtTokenUtil.generateToken(tokenContext);

            // We do not assert the full token string format in detail.
            // We only assert that generation returns a non-empty serialized JWT.
            org.junit.jupiter.api.Assertions.assertNotNull(token);
            org.junit.jupiter.api.Assertions.assertFalse(token.isBlank());
        }
    }

    /**
     * Tests related to JWT parsing and business validation.
     */
    @Nested
    @DisplayName("parseToken")
    class ParseTokenTests {

        /**
         * A generated token with valid issuer and subject should parse back
         * into the expected {@link TokenContext}.
         */
        @Test
        void should_parse_token_when_issuer_and_subject_match_expected_pattern() {
            TokenContext original = TokenContext.builder()
                    .issuer("ap12345")
                    .subject("a012345")
                    .build();

            String rawToken = JwtTokenUtil.generateToken(original);

            TokenContext parsed = JwtTokenUtil.parseToken(rawToken);

            assertEquals("ap12345", parsed.issuer());
            assertEquals("a012345", parsed.subject());
        }

        /**
         * If issuer or subject does not match the expected regex,
         * parsing must fail with a business exception.
         */
        @Test
        void should_throw_when_issuer_or_subject_does_not_match_expected_pattern() {
            TokenContext invalid = TokenContext.builder()
                    .issuer("invalid-issuer")
                    .subject("invalid-subject")
                    .build();

            String rawToken = JwtTokenUtil.generateToken(invalid);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> JwtTokenUtil.parseToken(rawToken)
            );

            assertEquals("[GIL_010]Invalid issuer and subject... do not match regex", exception.getMessage());
        }

        /**
         * A malformed raw token must be rejected as an invalid JWT format.
         */
        @Test
        void should_throw_when_raw_token_is_not_a_valid_jwt() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> JwtTokenUtil.parseToken("not-a-jwt")
            );

            assertEquals("Invalid JWT format", exception.getMessage());
        }
    }

    /**
     * Tests related to extracting and parsing token context from an HTTP request.
     */
    @Nested
    @DisplayName("getTokenContext")
    class GetTokenContextTests {

        /**
         * A valid Authorization header should be converted into the expected
         * {@link TokenContext}.
         */
        @Test
        void should_get_token_context_from_http_request() {
            TokenContext original = TokenContext.builder()
                    .issuer("ap12345")
                    .subject("ap54321")
                    .build();

            String rawToken = JwtTokenUtil.generateToken(original);
            when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + rawToken);

            TokenContext tokenContext = JwtTokenUtil.getTokenContext(httpServletRequest);

            assertEquals("ap12345", tokenContext.issuer());
            assertEquals("ap54321", tokenContext.subject());
        }

        /**
         * Any failure during extraction or parsing must be wrapped into
         * a {@link GilFlowException} with a generic token error message.
         */
        @Test
        void should_wrap_any_failure_into_gil_flow_exception() {
            when(httpServletRequest.getHeader("Authorization")).thenReturn(null);

            GilFlowException exception = assertThrows(
                    GilFlowException.class,
                    () -> JwtTokenUtil.getTokenContext(httpServletRequest)
            );

            assertEquals("[GIL_010]Failed to read or load token", exception.getMessage());
        }
    }
}

```

