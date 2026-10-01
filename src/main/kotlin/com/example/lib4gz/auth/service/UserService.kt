package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.MeCoursesResponse
import com.example.lib4gz.auth.model.payload.UpdateMeRequest
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.common.exception.ValidationException
import com.example.lib4gz.courses.service.CourseService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * The signed-in user's own account. The email is not edited here: it comes from the connected
 * Google account (see [GoogleAuthService.connect]).
 */
interface UserService {
    fun grantTeacherRole(requesterId: String, targetUserId: String): Mono<UserResponse>
    fun getMe(userId: String): Mono<UserResponse>
    fun updateMe(userId: String, request: UpdateMeRequest): Mono<UserResponse>
    fun getMyCourses(userId: String): Mono<MeCoursesResponse>
}

@Service
@Transactional
class UserServiceImpl(
    private val userRepo: UserRepo,
    private val courseService: CourseService,
    private val transactionTemplate: TransactionTemplate
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

    /** Saves the display name. The only field a person edits directly. */
    override fun updateMe(userId: String, request: UpdateMeRequest): Mono<UserResponse> {
        return Mono.fromCallable {
            transactionTemplate.execute {
                val user = findUser(userId)
                if (request.name != null) {
                    val name = request.name.trim()
                    if (name.isEmpty()) throw ValidationException("Name must not be blank")
                    user.name = name
                }
                userRepo.save(user).toResponse()
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
