### Overview
> Security model definitions: [models/common.yaml](../models/common.yaml#/security_models)

JWT-based stateless authentication and authorization infrastructure.

### Components (in order of initialization/request flow):

#### 1. SecurityConfigLoader (`common/config/security/loader/SecurityConfigLoader.kt`)
- Package: `com.example.lib4gz.common.config.security.loader`
- @Service class AppSecurityConfigLoader
- @PostConstruct init() loads config on startup
- loadConfig(): SecurityConfigProperties - reads `security.json` from classpath using ObjectMapper with KotlinModule

#### 2. Security Config Entities (`common/config/security/loader/Entity.kt`)
- Package: `com.example.lib4gz.common.config.security.loader`
- data class SecurityConfigProperties(val permittedEndpoints: List&lt;String&gt; = emptyList(), val authorizedEndpoints: List&lt;AuthorizedEndpoint&gt; = emptyList(), val cors: CorsConfig = CorsConfig())
- data class AuthorizedEndpoint(val endpoint: String, val roles: List&lt;String&gt;)
- data class CorsConfig(val allowedOrigins: List&lt;String&gt; = emptyList(), val allowedMethods: List&lt;String&gt; = emptyList(), val allowedHeaders: List&lt;String&gt; = emptyList(), val allowCredentials: Boolean = true, val maxAge: Long = 3600)

#### 3. AuthConfig (`common/config/security/AuthConfig.kt`)
- Package: `com.example.lib4gz.common.config.security`
- @Configuration
- Beans:
  - @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
  - @Bean fun authenticationProvider(userDetailsService: CustomUserDetailService, passwordEncoder: PasswordEncoder): DaoAuthenticationProvider - sets userDetailsService and passwordEncoder
  - @Bean fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

#### 4. CustomUserDetailService (`common/config/security/CustomUserDetailService.kt`)
- Package: `com.example.lib4gz.common.config.security`
- @Service, implements UserDetailsService
- Constructor: userRepo: UserRepo
- loadUserByUsername(username: String): UserDetails - finds user by username via userRepo.findByUsername(), throws UsernameNotFoundException if null, returns CustomUserDetails(user)

#### 5. CustomUserDetails (`common/config/security/CustomUserDetails.kt`)
- Package: `com.example.lib4gz.common.config.security`
- class CustomUserDetails(private val user: User) : UserDetails
- fun getUser(): UserResponse = user.toResponse()
- getAuthorities(): maps user.roles to SimpleGrantedAuthority
- getPassword(): user.password ?: ""
- getUsername(): user.email (NOTE: returns EMAIL, not username)
- isAccountNonExpired(): true
- isAccountNonLocked(): true
- isCredentialsNonExpired(): true
- isEnabled(): user.status == UserStatus.ACTIVE

#### 6. JwtProvider (`common/config/security/JwtProvider.kt`)
- Package: `com.example.lib4gz.common.config.security`
- @Component
- Constructor injection from application.yaml:
  - @Value("\${application.security.jwt.secret-key}") secretKey: String
  - @Value("\${application.security.jwt.refresh-token.expiration}") refreshExpiration: Long
  - @Value("\${application.security.jwt.expiration}") tokenExpiration: Long
- Methods:
  - getSigningKey(): SecretKey - Base64 decode secretKey, create HMAC-SHA key
  - getUsernameFromToken(token): String - extracts subject claim
  - getClaimsFromToken&lt;T&gt;(token, claimsResolver): T - generic claim extraction
  - getAllClaimsFromToken(token): Claims - Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).payload
  - getUserIdFromToken(token): String - extracts "user.id" from claims map. Gets "user" claim as Map, then gets "id" from it.
  - generateAccessToken(user: User): Token - creates claims map with "user" key containing UserResponse (via user.toResponse()), calls doGenerateToken, returns Token(token, expiredAt)
  - generateRefreshToken(user: User): String - empty claims, returns token string
  - doGenerateToken(claims: Map&lt;String, Any&gt;, subject: String, expiration: Long): String - builds JWT with Jwts.builder().claims(claims).subject(subject).issuedAt(now).expiration(now+expiration).signWith(getSigningKey()).compact()
  - validateToken(token: String): Boolean - tries parsing, catches MalformedJwtException, ExpiredJwtException, UnsupportedJwtException, IllegalArgumentException, returns false on any

#### 7. JwtAuthFilter (`common/config/security/JwtAuthFilter.kt`)
- Package: `com.example.lib4gz.common.config.security`
- @Component, extends OncePerRequestFilter
- Constructor: jwtProvider: JwtProvider, customUserDetailService: CustomUserDetailService
- doFilterInternal flow:
  1. If auth request (path contains "/v1/register" or "/v1/login"), pass through (filterChain.doFilter)
  2. Create MutableHttpServletRequest wrapper
  3. Remove "X-User-Id" header from request (security: prevent spoofing)
  4. Extract JWT from "access_token" header
  5. If token is not null and validates:
     a. Get username from token
     b. Get userId from token (getUserIdFromToken)
     c. Add "X-User-Id" header to mutable request
     d. Load UserDetails via customUserDetailService.loadUserByUsername(username)
     e. Create UsernamePasswordAuthenticationToken with userDetails, null credentials, authorities
     f. Set SecurityContextHolder authentication
  6. filterChain.doFilter(mutableRequest, response)

#### 8. AuthEntryPoint (`common/config/security/AuthEntryPoint.kt`)
- Package: `com.example.lib4gz.common.config.security`
- @Component, implements AuthenticationEntryPoint, Serializable
- Constructor: @Qualifier("handlerExceptionResolver") resolver: HandlerExceptionResolver
- commence(): delegates to resolver.resolveException()

#### 9. SecurityConfig (`common/config/security/SecurityConfig.kt`)
- Package: `com.example.lib4gz.common.config.security`
- @Configuration @EnableWebSecurity
- Constructor: jwtAuthFilter, authenticationProvider: DaoAuthenticationProvider, authEntryPoint, securityConfigLoader
- @Bean securityFilterChain(http: HttpSecurity): SecurityFilterChain:
  - csrf disabled
  - cors enabled with corsConfigurationSource()
  - authorizeHttpRequests:
    - permittedEndpoints from security.json -> permitAll()
    - authorizedEndpoints from security.json -> hasAnyRole (currently empty)
    - DispatcherType.ASYNC, DispatcherType.FORWARD -> permitAll()
    - anyRequest() -> authenticated()
  - exceptionHandling with authEntryPoint
  - JwtAuthFilter before UsernamePasswordAuthenticationFilter
  - sessionManagement STATELESS
- Private corsConfigurationSource(): reads CORS config from security.json, registers for "/**"

#### 10. GoogleTokenVerifier (`common/config/security/GoogleTokenVerifier.kt`)
See [Google ID token verification](#google-id-token-verification) below.

#### 11. Request Header Constants (`common/config/common/RequestHeader.kt`)
- Package: `com.example.lib4gz.common.config.common`
- object PZRequestHeader { const val USER_ID = "X-User-Id" }

#### 12. MutableHttpServletRequest (`common/config/common/MutableHttpServletRequest.kt`)
- Package: `com.example.lib4gz.common.config.common`
- class MutableHttpServletRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request)
- Private customHeaders: MutableMap&lt;String, String&gt;
- Private removedHeaders: MutableSet&lt;String&gt;
- fun addHeader(name: String, value: String)
- fun removeHeader(name: String)
- Overrides: getHeader(), getHeaderNames(), getHeaders() - merges custom headers and excludes removed headers

### security.json (src/main/resources/security.json)
```json
{
  "permittedEndpoints": ["/v1/auth/login", "/v1/auth/register/**", "/v1/auth/google", "/v2/api-docs", "/v3/api-docs", "/v3/api-docs/**", "/swagger-resources", "/swagger-resources/**", "/configuration/ui", "/configuration/security", "/swagger-ui/**", "/webjars/**", "/swagger-ui.html", "/error"],
  "authorizedEndpoints": [],
  "corsSettings": {
    "allowedOrigins": ["http://localhost:3000", "http://localhost:3001"],
    "allowedMethods": ["HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"],
    "allowedHeaders": ["Origin", "Authorization", "Cache-Control", "Content-Type", "access_token"],
    "allowCredentials": true,
    "maxAge": 3600
  }
}
```

Public auth routes:

| Pattern | Covers |
|---------|--------|
| `/v1/auth/login` | Password login (username or email) |
| `/v1/auth/register/**` | `/v1/auth/register/email`, `/v1/auth/register/email/verify`, `/v1/auth/register/complete` |
| `/v1/auth/google` | Google sign-in |

The old `/v1/auth/register` entry was replaced by `/v1/auth/register/**` when username/password registration was removed. `/v1/auth/refresh-token` is not listed. All `/v1/users/**` routes (including `/v1/users/me/**`) fall under `anyRequest().authenticated()`.

### Google ID Token Verification

`common/config/security/GoogleTokenVerifier.kt`, used by `GoogleAuthServiceImpl` for `POST /v1/auth/google`. The browser gets an ID token from the Google Identity Services button and posts it; there is no Spring `oauth2Login`, no server session, no redirect URI, and the client secret is unused.

- `data class GoogleIdentity(sub: String, email: String, emailVerified: Boolean, name: String?, picture: String?)`
- `interface GoogleTokenVerifier { fun verify(idToken: String): GoogleIdentity }`
- `@Component class NimbusGoogleTokenVerifier(@Value("\${application.security.google.client-id}") clientId: String)`
  - Decoder: `NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")` (from `spring-security-oauth2-jose`). Google's keys are fetched on first use and cached; no network call at startup.
  - Validators (`DelegatingOAuth2TokenValidator`):
    - `JwtTimestampValidator()` — `exp` / `nbf`
    - `iss` must be `https://accounts.google.com` or `accounts.google.com` (Google issues both spellings)
    - `aud` must contain `clientId`, and `clientId` must be non-blank (so an unset `GOOGLE_CLIENT_ID` rejects every token)
  - Claims: `sub`, `email` (missing → invalid), `email_verified` (missing → `false`), `name`, `picture`.
  - Any `JwtException` → `ResponseStatusException(401, "Invalid Google token")`.
- `GoogleAuthServiceImpl` additionally rejects `emailVerified == false` with 401 `"Google account email is not verified"`, then applies the account-matching rules in [features/auth.md](../features/auth.md#google-sign-in--googleauthserviceimplsignin).
- The verifier is an interface so unit tests (`GoogleAuthServiceImplTest`) mock it.

### Password Login

`AuthServiceImpl.auth()` looks the user up by email (case-insensitive) when the `username` field contains `@`, otherwise by username, and compares with `PasswordEncoder` (BCrypt). A missing user, a user without a password (Google-only account), and a wrong password all return 401 `"Invalid username or password"`. `JwtProvider.generateAccessToken(user)` then embeds the widened `UserResponse` (id, username, email, name, avatarUrl, role, emailVerified, pendingEmail) in the `user` claim; `JwtAuthFilter` still reads only `user.id` from that claim, so tokens issued before the change stay valid.

### Authentication Flow
1. Client sends `access_token` header (NOT `Authorization: Bearer`)
2. JwtAuthFilter extracts and validates token
3. Filter injects `X-User-Id` header into request
4. Controllers read `@RequestHeader(PZRequestHeader.USER_ID) userId: UUID`
