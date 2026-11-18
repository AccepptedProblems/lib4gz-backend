package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.model.payload.CompleteRegistrationRequest
import com.example.lib4gz.auth.model.payload.GoogleSignInRequest
import com.example.lib4gz.auth.model.payload.GoogleSignInResponse
import com.example.lib4gz.auth.model.payload.LoginReq
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.OtpSentResponse
import com.example.lib4gz.auth.model.payload.RefreshTokenRequest
import com.example.lib4gz.auth.model.payload.RegistrationStartedResponse
import com.example.lib4gz.auth.model.payload.StartEmailRegistrationRequest
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.model.payload.VerifyEmailRegistrationRequest
import com.example.lib4gz.auth.service.AuthService
import com.example.lib4gz.auth.service.GoogleAuthService
import com.example.lib4gz.auth.service.RefreshTokenService
import com.example.lib4gz.auth.service.RegistrationService
import com.example.lib4gz.common.config.common.PZRequestHeader
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val registrationService: RegistrationService,
    private val googleAuthService: GoogleAuthService,
    private val refreshTokenService: RefreshTokenService
) {
    @PostMapping("/login")
    fun login(@RequestBody loginReq: LoginReq): Mono<LoginResponse> {
        return authService.auth(loginReq)
    }

    // The two Google actions are custom methods on one resource, hence the colon.
    @PostMapping("/google:sign-in")
    fun googleSignIn(@RequestBody request: GoogleSignInRequest): Mono<GoogleSignInResponse> {
        return googleAuthService.signIn(request.idToken)
    }

    /** Needs a token, unlike everything else here: it changes the caller's own account. */
    @PostMapping("/google:connect")
    fun googleConnect(
        @RequestHeader(PZRequestHeader.USER_ID) userId: String,
        @RequestBody request: GoogleSignInRequest
    ): Mono<UserResponse> {
        return googleAuthService.connect(userId, request.idToken)
    }

    @PostMapping("/register/email")
    fun startEmailRegistration(@RequestBody request: StartEmailRegistrationRequest): Mono<OtpSentResponse> {
        return registrationService.startEmail(request.email)
    }

    @PostMapping("/register/email/verify")
    fun verifyEmailRegistration(@RequestBody request: VerifyEmailRegistrationRequest): Mono<RegistrationStartedResponse> {
        return registrationService.verifyEmail(request.email, request.code)
    }

    @PostMapping("/register/complete")
    fun completeRegistration(@RequestBody request: CompleteRegistrationRequest): Mono<LoginResponse> {
        return registrationService.complete(request)
    }

    /** Public, so it works with an expired access token. Single use: the response carries the next refresh token. */
    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): Mono<LoginResponse> {
        return refreshTokenService.refresh(request.refreshToken)
    }

    /** Public, so a browser whose access token has expired can still sign out. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestBody request: RefreshTokenRequest): Mono<Void> {
        return refreshTokenService.logout(request.refreshToken)
    }
}
