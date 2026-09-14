package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.OtpPurpose
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.MeCoursesResponse
import com.example.lib4gz.auth.model.payload.OtpSentResponse
import com.example.lib4gz.auth.model.payload.UpdateMeRequest
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.common.exception.ValidationException
import com.example.lib4gz.courses.service.CourseService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface UserService {
    fun grantTeacherRole(requesterId: String, targetUserId: String): Mono<UserResponse>
    fun getMe(userId: String): Mono<UserResponse>
    fun updateMe(userId: String, request: UpdateMeRequest): Mono<UserResponse>
    fun getMyCourses(userId: String): Mono<MeCoursesResponse>
    /** Confirms the pending email (swapping it in) or, with none pending, the current one. */
    fun verifyEmail(userId: String, code: String): Mono<UserResponse>
    fun resendEmailCode(userId: String): Mono<OtpSentResponse>
}

@Service
@Transactional
class UserServiceImpl(
    private val userRepo: UserRepo,
    private val courseService: CourseService,
    private val otpService: OtpService,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${application.otp.resend-cooldown-ms}") private val resendCooldownMs: Long
) : UserService {

    override fun grantTeacherRole(requesterId: String, targetUserId: String): Mono<UserResponse> {
        return Mono.fromCallable {
            val requester = userRepo.findById(requesterId).orElseThrow {
                ResourceNotFoundException("User not found with id: $requesterId")
            }

            if (!requester.isAdmin()) {
                throw UnauthorizedException("Only users with ADMIN role can grant teacher role")
            }

            val targetUser = userRepo.findById(targetUserId).orElseThrow {
                ResourceNotFoundException("User not found with id: $targetUserId")
            }

            if (targetUser.hasRole(UserRole.TEACHER)) {
                throw BusinessException("User already has the TEACHER role")
            }

            targetUser.roles.add(UserRole.TEACHER)
            val savedUser = userRepo.save(targetUser)
            savedUser.toResponse()
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getMe(userId: String): Mono<UserResponse> {
        return Mono.fromCallable {
            findUser(userId).toResponse()
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateMe(userId: String, request: UpdateMeRequest): Mono<UserResponse> {
        return Mono.fromCallable {
            // One transaction, so a mail failure also undoes `pendingEmail`.
            transactionTemplate.execute {
                val user = findUser(userId)
                if (request.name != null) {
                    val name = request.name.trim()
                    if (name.isEmpty()) throw ValidationException("Name must not be blank")
                    user.name = name
                }
                if (request.email != null) {
                    requestEmailChange(user, request.email)
                }
                userRepo.save(user).toResponse()
            }!!
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun requestEmailChange(user: User, rawEmail: String) {
        val email = rawEmail.trim().lowercase()
        if (email.isEmpty()) throw ValidationException("Email must not be blank")
        if (!email.contains('@')) throw ValidationException("Invalid email")

        when {
            email.equals(user.email, ignoreCase = true) -> user.pendingEmail = null
            email == user.pendingEmail -> Unit // code already sent; resend has its own endpoint
            userRepo.existsByEmailIgnoreCaseAndIdNot(email, user.id) ->
                throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
            else -> {
                user.pendingEmail = email
                otpService.issue(email, OtpPurpose.EMAIL_CHANGE)
            }
        }
    }

    override fun verifyEmail(userId: String, code: String): Mono<UserResponse> {
        return Mono.fromCallable {
            transactionTemplate.execute {
                val user = findUser(userId)
                val pending = user.pendingEmail
                // Verify first: it commits its own attempt counter (see OtpService.verify).
                otpService.verify(pending ?: user.email, OtpPurpose.EMAIL_CHANGE, code)
                if (pending != null) {
                    if (userRepo.existsByEmailIgnoreCaseAndIdNot(pending, user.id)) {
                        throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
                    }
                    user.email = pending
                    user.pendingEmail = null
                }
                user.emailVerified = true
                userRepo.save(user).toResponse()
            }!!
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun resendEmailCode(userId: String): Mono<OtpSentResponse> {
        return Mono.fromCallable {
            transactionTemplate.execute {
                val user = findUser(userId)
                if (user.pendingEmail == null && user.emailVerified) {
                    throw BusinessException("Email is already verified")
                }
                val target = user.pendingEmail ?: user.email
                otpService.issue(target, OtpPurpose.EMAIL_CHANGE)
                OtpSentResponse(target, resendCooldownMs / 1000)
            }!!
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getMyCourses(userId: String): Mono<MeCoursesResponse> {
        // The two lists are independent: issue both in parallel, then combine.
        return Mono.zip(
            courseService.listEnrolledCourses(userId).collectList(),
            courseService.listUserCreatedCourses(userId).collectList()
        ).map { tuple ->
            MeCoursesResponse(enrolled = tuple.t1, created = tuple.t2)
        }
    }

    private fun findUser(userId: String) = userRepo.findById(userId).orElseThrow {
        ResourceNotFoundException("User not found with id: $userId")
    }
}
