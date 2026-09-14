package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.model.payload.MeCoursesResponse
import com.example.lib4gz.auth.model.payload.OtpSentResponse
import com.example.lib4gz.auth.model.payload.UpdateMeRequest
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.model.payload.VerifyEmailCodeRequest
import com.example.lib4gz.auth.service.UserService
import com.example.lib4gz.common.config.common.PZRequestHeader
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

/**
 * `/me` endpoints always act on the authenticated caller (X-User-Id from JwtAuthFilter).
 * Spring matches the literal `me` segment before `/{userId}`.
 */
@RestController
@RequestMapping("/v1/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    fun getMe(@RequestHeader(PZRequestHeader.USER_ID) userId: String): Mono<UserResponse> {
        return userService.getMe(userId)
    }

    @PatchMapping("/me")
    fun updateMe(
        @RequestHeader(PZRequestHeader.USER_ID) userId: String,
        @RequestBody request: UpdateMeRequest
    ): Mono<UserResponse> {
        return userService.updateMe(userId, request)
    }

    @PostMapping("/me/email/verify")
    fun verifyEmail(
        @RequestHeader(PZRequestHeader.USER_ID) userId: String,
        @RequestBody request: VerifyEmailCodeRequest
    ): Mono<UserResponse> {
        return userService.verifyEmail(userId, request.code)
    }

    @PostMapping("/me/email/resend")
    fun resendEmailCode(@RequestHeader(PZRequestHeader.USER_ID) userId: String): Mono<OtpSentResponse> {
        return userService.resendEmailCode(userId)
    }

    @GetMapping("/me/courses")
    fun getMyCourses(@RequestHeader(PZRequestHeader.USER_ID) userId: String): Mono<MeCoursesResponse> {
        return userService.getMyCourses(userId)
    }

    @PostMapping("/{userId}/grant-teacher")
    fun grantTeacherRole(
        @PathVariable userId: String,
        @RequestHeader(PZRequestHeader.USER_ID) requesterId: String
    ): Mono<UserResponse> {
        return userService.grantTeacherRole(requesterId, userId)
    }
}
