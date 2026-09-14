package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.model.payload.CompleteRegistrationRequest
import com.example.lib4gz.auth.model.payload.GoogleSignInRequest
import com.example.lib4gz.auth.model.payload.GoogleSignInResponse
import com.example.lib4gz.auth.model.payload.LoginReq
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.OtpSentResponse
import com.example.lib4gz.auth.model.payload.RegistrationStartedResponse
import com.example.lib4gz.auth.model.payload.StartEmailRegistrationRequest
import com.example.lib4gz.auth.model.payload.VerifyEmailRegistrationRequest
import com.example.lib4gz.auth.service.AuthService
import com.example.lib4gz.auth.service.GoogleAuthService
import com.example.lib4gz.auth.service.RegistrationService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val registrationService: RegistrationService,
    private val googleAuthService: GoogleAuthService
) {
    @PostMapping("/login")
    fun login(@RequestBody loginReq: LoginReq): Mono<LoginResponse> {
        return authService.auth(loginReq)
    }

    @PostMapping("/google")
    fun googleSignIn(@RequestBody request: GoogleSignInRequest): Mono<GoogleSignInResponse> {
        return googleAuthService.signIn(request.idToken)
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

    @PostMapping("/refresh-token")
    fun refreshToken() {

    }
}
