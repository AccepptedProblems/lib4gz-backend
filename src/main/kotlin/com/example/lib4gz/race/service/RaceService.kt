package com.example.lib4gz.race.service

import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.race.model.entity.RaceQuestion
import com.example.lib4gz.race.model.entity.RaceResult
import com.example.lib4gz.race.model.mapper.RaceMapper
import com.example.lib4gz.race.model.payload.CreateRaceResultRequest
import com.example.lib4gz.race.model.payload.LeaderboardResponse
import com.example.lib4gz.race.model.payload.RaceQuestionItem
import com.example.lib4gz.race.model.payload.RaceQuestionResponse
import com.example.lib4gz.race.model.payload.RaceResultResponse
import com.example.lib4gz.race.model.payload.ReplaceRaceQuestionsRequest
import com.example.lib4gz.race.repo.RaceQuestionRepo
import com.example.lib4gz.race.repo.RaceResultRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface RaceService {

    fun listQuestions(courseId: String, userId: String): Flux<RaceQuestionResponse>

    fun replaceQuestions(courseId: String, userId: String, request: ReplaceRaceQuestionsRequest): Flux<RaceQuestionResponse>

    fun saveResult(courseId: String, userId: String, request: CreateRaceResultRequest): Mono<RaceResultResponse>

    fun leaderboard(courseId: String, moduleId: String?, userId: String): Mono<LeaderboardResponse>
}

@Service
class RaceServiceImpl(
    private val raceQuestionRepo: RaceQuestionRepo,
    private val raceResultRepo: RaceResultRepo,
    private val courseRepo: CourseRepo,
    private val moduleRepo: ModuleRepo,
    private val userRepo: UserRepo,
    private val enrollmentService: EnrollmentService,
    private val raceMapper: RaceMapper,
    private val transactionTemplate: TransactionTemplate
) : RaceService {

    override fun listQuestions(courseId: String, userId: String): Flux<RaceQuestionResponse> {
        return Mono.fromCallable {
            requireEnrolled(courseId, userId)
            raceQuestionRepo.findByCourseIdOrderByOrderIndexAscCreatedAtAsc(courseId).map { raceMapper.toResponse(it) }
        }.flatMapMany { Flux.fromIterable(it) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    override fun replaceQuestions(
        courseId: String,
        userId: String,
        request: ReplaceRaceQuestionsRequest
    ): Flux<RaceQuestionResponse> {
        return Mono.fromCallable {
            requireCourse(courseId)
            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only a teacher of this course can edit its race questions")
            }
            val moduleIds = moduleRepo.findByCourse_Id(courseId).map { it.id }.toSet()
            val seenIds = mutableSetOf<String>()
            request.questions.forEachIndexed { index, item ->
                validate(index + 1, item, moduleIds)
                if (item.id != null && !seenIds.add(item.id)) {
                    throw BadRequestException("Question ${index + 1} is listed twice.")
                }
            }

            transactionTemplate.execute {
                val existing = raceQuestionRepo.findByCourseId(courseId).associateBy { it.id }
                val keptIds = request.questions.mapNotNull { it.id }.toSet()
                existing.values.filter { it.id !in keptIds }.forEach { raceQuestionRepo.delete(it) }

                val questions = request.questions.mapIndexed { index, item ->
                    val options = item.options.map { mapOf("text" to it) }
                    if (item.id == null) {
                        RaceQuestion(
                            courseId = courseId,
                            moduleId = item.moduleId,
                            content = item.content,
                            options = options,
                            correctIndex = item.correctIndex,
                            orderIndex = index
                        )
                    } else {
                        val question = existing[item.id] ?: throw BadRequestException(
                            "Question ${index + 1} is no longer in this course's bank. Reload the page and save again."
                        )
                        question.moduleId = item.moduleId
                        question.content = item.content
                        question.options = options
                        question.correctIndex = item.correctIndex
                        question.orderIndex = index
                        question
                    }
                }
                raceQuestionRepo.saveAll(questions).map { raceMapper.toResponse(it) }
            }!!
        }.flatMapMany { Flux.fromIterable(it) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun validate(number: Int, item: RaceQuestionItem, moduleIds: Set<String>) {
        if (item.content.isBlank()) {
            throw BadRequestException("Question $number has no text.")
        }
        if (item.options.size !in 2..6) {
            throw BadRequestException("Question $number needs between 2 and 6 options; it has ${item.options.size}.")
        }
        if (item.options.any { it.isBlank() }) {
            throw BadRequestException("Question $number has an empty option.")
        }
        if (item.correctIndex !in item.options.indices) {
            throw BadRequestException("Question $number marks an option that does not exist as correct.")
        }
        if (item.moduleId !in moduleIds) {
            throw BadRequestException("Question $number is assigned to a module that is not in this course.")
        }
    }

    override fun saveResult(courseId: String, userId: String, request: CreateRaceResultRequest): Mono<RaceResultResponse> {
        return Mono.fromCallable {
            requireEnrolled(courseId, userId)
            if (request.moduleId != null && !moduleRepo.existsByIdAndCourse_Id(request.moduleId, courseId)) {
                throw BadRequestException("That module is not in this course.")
            }
            if (request.finishMs <= 0) {
                throw BadRequestException("The finish time must be positive.")
            }
            if (request.correctCount < 0 || request.wrongCount < 0) {
                throw BadRequestException("Answer counts cannot be negative.")
            }

            transactionTemplate.execute {
                val result = RaceResult(
                    userId = userId,
                    courseId = courseId,
                    moduleId = request.moduleId,
                    finishMs = request.finishMs,
                    correctCount = request.correctCount,
                    wrongCount = request.wrongCount,
                    seed = request.seed
                )
                raceMapper.toResponse(raceResultRepo.save(result))
            }!!
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun leaderboard(courseId: String, moduleId: String?, userId: String): Mono<LeaderboardResponse> {
        return Mono.fromCallable {
            requireEnrolled(courseId, userId)

            val best = if (moduleId == null) {
                raceResultRepo.findBestPerUserInCourse(courseId)
            } else {
                raceResultRepo.findBestPerUserInModule(courseId, moduleId)
            }
            val mine = if (moduleId == null) {
                raceResultRepo.findTopByUserIdAndCourseIdAndModuleIdIsNullOrderByFinishMsAscCreatedAtAsc(userId, courseId)
            } else {
                raceResultRepo.findTopByUserIdAndCourseIdAndModuleIdOrderByFinishMsAscCreatedAtAsc(userId, courseId, moduleId)
            }

            val userIds = best.map { it.userId }.toSet() + listOfNotNull(mine?.userId)
            val users = userRepo.findAllById(userIds).associateBy { it.id }

            LeaderboardResponse(
                rows = best.map { raceMapper.toRow(it, users[it.userId]) },
                mine = mine?.let { raceMapper.toRow(it, users[it.userId]) }
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun requireCourse(courseId: String) {
        if (!courseRepo.existsById(courseId)) {
            throw ResourceNotFoundException("Course not found with id: $courseId")
        }
    }

    private fun requireEnrolled(courseId: String, userId: String) {
        requireCourse(courseId)
        if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
            throw UnauthorizedException("You need an active enrollment in this course to race")
        }
    }
}
