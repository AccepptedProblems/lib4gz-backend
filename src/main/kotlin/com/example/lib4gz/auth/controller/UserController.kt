package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.service.UserService
import com.example.lib4gz.common.config.common.PZRequestHeader
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/users")
class UserController(
    private val userService: UserService
) {

    @PostMapping("/{userId}/grant-teacher")
    fun grantTeacherRole(
        @PathVariable userId: String,
        @RequestHeader(PZRequestHeader.USER_ID) requesterId: String
    ): Mono<UserResponse> {
        return userService.grantTeacherRole(requesterId, userId)
    }
}
