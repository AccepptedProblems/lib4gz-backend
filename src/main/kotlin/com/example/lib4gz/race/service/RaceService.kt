package com.example.lib4gz.race.service

import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.race.model.entity.Race
import com.example.lib4gz.race.model.entity.RaceQuestion
import com.example.lib4gz.race.model.entity.RaceResult
import com.example.lib4gz.race.model.mapper.RaceMapper
import com.example.lib4gz.race.model.payload.AnswerRequest
import com.example.lib4gz.race.model.payload.AnswerResponse
import com.example.lib4gz.race.model.payload.DealtQuestion
import com.example.lib4gz.race.model.payload.FinishRaceRequest
import com.example.lib4gz.race.model.payload.LeaderboardResponse
import com.example.lib4gz.race.model.payload.RaceQuestionItem
import com.example.lib4gz.race.model.payload.RaceQuestionResponse
import com.example.lib4gz.race.model.payload.RaceResultResponse
import com.example.lib4gz.race.model.payload.ReplaceRaceQuestionsRequest
import com.example.lib4gz.race.model.payload.StartRaceRequest
import com.example.lib4gz.race.model.payload.StartRaceResponse
import com.example.lib4gz.race.repo.RaceQuestionRepo
import com.example.lib4gz.race.repo.RaceRepo
import com.example.lib4gz.race.repo.RaceResultRepo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.SecureRandom
import java.time.Instant
import java.util.Random

interface RaceService {

    fun listQuestions(courseId: String, userId: String): Flux<RaceQuestionResponse>

    fun replaceQuestions(courseId: String, userId: String, request: ReplaceRaceQuestionsRequest): Flux<RaceQuestionResponse>

    fun startRace(courseId: String, userId: String, request: StartRaceRequest): Mono<StartRaceResponse>

    fun answer(courseId: String, raceId: String, userId: String, request: AnswerRequest): Mono<AnswerResponse>

    fun finishRace(courseId: String, raceId: String, userId: String, request: FinishRaceRequest): Mono<RaceResultResponse>

    fun leaderboard(courseId: String, moduleId: String?, userId: String): Mono<LeaderboardResponse>
}

/** The most options one dealt question shows: the game has four answer cards. */
internal const val MAX_SHOWN_OPTIONS = 4

/** How many wrong questions a finish sends the key of. Copies the game's `missedShown`. */
internal const val MAX_SHOWN_KEYS = 4

/**
 * The finish reply's key: [answerKey]'s entry at the first [MAX_SHOWN_KEYS] numbers of [wrongSeqs],
 * which is in race order, and null at every other question number.
 */
internal fun shownKey(answerKey: List<Int>, wrongSeqs: List<Int>): List<Int?> {
    val shown = wrongSeqs.take(MAX_SHOWN_KEYS).toSet()
    return answerKey.mapIndexed { seq, key -> key.takeIf { seq in shown } }
}

/** A dealt question: the options in the order to show and the right one among them. */
internal class Card(val question: RaceQuestion, val options: List<String>, val key: Int)

/**
 * [count] cards from [bank]. Each question shows its right option and up to three wrong ones whose
 * text differs from it, in an order from [random]. The bank is shuffled and dealt; when it runs out it
 * is reshuffled, never giving the same question twice in a row, as the game's deck does.
 */
internal fun deal(bank: List<RaceQuestion>, count: Int, random: Random): List<Card> {
    val cards = bank.map { question ->
        val texts = question.optionTexts()
        val right = texts[question.correctIndex]
        // The same wrong options in every deal, picked by the question's id. Picked afresh each time,
        // the right option would be the only one in every deal, and a few deals would show it.
        val wrong = texts.filter { it != right }.distinct()
            .shuffled(Random(question.id.hashCode().toLong()))
            .take(MAX_SHOWN_OPTIONS - 1)
        val shown = (wrong + right).shuffled(random)
        Card(question, shown, shown.indexOf(right))
    }
    val deck = mutableListOf<Card>()
    while (deck.size < count) {
        val pass = cards.shuffled(random).toMutableList()
        if (pass.size > 1 && pass.first() === deck.lastOrNull()) pass.add(pass.removeAt(0))
        deck += pass
    }
    return deck.take(count)
}

@Service
class RaceServiceImpl(
    private val raceQuestionRepo: RaceQuestionRepo,
    private val raceResultRepo: RaceResultRepo,
    private val raceRepo: RaceRepo,
    private val courseRepo: CourseRepo,
    private val moduleRepo: ModuleRepo,
    private val userRepo: UserRepo,
    private val enrollmentService: EnrollmentService,
    private val raceMapper: RaceMapper,
    private val transactionTemplate: TransactionTemplate,
    private val properties: RaceProperties
) : RaceService {

    // Not seeded from the race's seed: the browser knows that seed, and could undo the option shuffle
    // with it to find where the teacher typed the right answer.
    private val random = SecureRandom()

    override fun listQuestions(courseId: String, userId: String): Flux<RaceQuestionResponse> {
        return Mono.fromCallable {
            requireCourse(courseId)
            val teacher = enrollmentService.isTeacherInCourse(courseId, userId)
            if (!teacher && !enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException(NOT_ENROLLED)
            }
            raceQuestionRepo.findByCourseIdOrderByOrderIndexAscCreatedAtAsc(courseId)
                .map { raceMapper.toResponse(it, teacher) }
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
                raceQuestionRepo.saveAll(questions).map { raceMapper.toResponse(it, teacher = true) }
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

    override fun startRace(courseId: String, userId: String, request: StartRaceRequest): Mono<StartRaceResponse> {
        return Mono.fromCallable {
            requireEnrolled(courseId, userId)
            if (request.questionCount < 1) {
                throw BadRequestException("A race needs at least one question.")
            }
            // A module of another course finds no questions, so it needs no check of its own.
            val bank = if (request.moduleId == null) {
                raceQuestionRepo.findByCourseId(courseId)
            } else {
                raceQuestionRepo.findByCourseIdAndModuleId(courseId, request.moduleId)
            }
            if (bank.isEmpty()) {
                throw BadRequestException("There are no race questions here yet.")
            }

            val deck = deal(bank, minOf(request.questionCount, properties.maxQuestions), random)
            val race = raceRepo.save(
                Race(
                    userId = userId,
                    courseId = courseId,
                    moduleId = request.moduleId,
                    seed = random.nextLong(1L shl 32),
                    questionIds = deck.map { it.question.id },
                    answerKey = deck.map { it.key }
                )
            )
            StartRaceResponse(
                raceId = race.id,
                seed = race.seed,
                questions = deck.mapIndexed { seq, card ->
                    DealtQuestion(
                        seq = seq,
                        questionId = card.question.id,
                        moduleId = card.question.moduleId,
                        content = card.question.content,
                        options = card.options
                    )
                }
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    // No enrolment check: the race row names its user, and start checked enrolment once.
    override fun answer(courseId: String, raceId: String, userId: String, request: AnswerRequest): Mono<AnswerResponse> {
        return Mono.fromCallable {
            if (request.seq < 0) {
                throw BadRequestException(NOT_IN_RACE)
            }
            if (request.choice != null && request.choice !in 0 until MAX_SHOWN_OPTIONS) {
                throw BadRequestException("That answer is not one of the options.")
            }

            val verdict = raceRepo.answer(raceId, userId, courseId, request.seq, request.choice ?: TIMEOUT)
            if (verdict != null) {
                AnswerResponse(request.seq, verdict.lastCorrect, verdict.correctCount, verdict.wrongCount)
            } else {
                // Refused. One read to tell a resend, which gets the stored verdict, from the rest.
                val race = findRace(courseId, raceId, userId)
                when {
                    request.seq == race.nextSeq - 1 && race.lastCorrect != null ->
                        AnswerResponse(request.seq, race.lastCorrect, race.correctCount, race.wrongCount)
                    race.finishedAt != null -> throw conflict(RACE_OVER)
                    request.seq >= race.answerKey.size -> throw BadRequestException(NOT_IN_RACE)
                    else -> throw conflict("That question has already been answered or skipped.")
                }
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun finishRace(
        courseId: String,
        raceId: String,
        userId: String,
        request: FinishRaceRequest
    ): Mono<RaceResultResponse> {
        return Mono.fromCallable {
            val race = findRace(courseId, raceId, userId)
            if (race.finishedAt != null) {
                throw conflict(RACE_OVER)
            }
            val now = Instant.now().toEpochMilli()
            if (request.finishMs <= 0) {
                throw BadRequestException("The finish time must be positive.")
            }
            if (request.finishMs > now - race.startedAt) {
                throw BadRequestException("That finish time is longer than the race has been running.")
            }
            if (request.finishMs < properties.floorMs(race.correctCount)) {
                throw BadRequestException("That finish time is faster than your right answers allow.")
            }

            transactionTemplate.execute {
                // The counts and wrong numbers come from the finishing update, not the read above: an
                // answer may have landed in between. The floor needs no second check, since counts
                // only rise and a higher right count only lowers the floor.
                val counts = raceRepo.markFinished(race.id, now) ?: throw conflict(RACE_OVER)
                val wrongSeqs: List<Int> = JSON.readValue(counts.wrongSeqs)
                val result = RaceResult(
                    userId = userId,
                    courseId = race.courseId,
                    moduleId = race.moduleId,
                    finishMs = request.finishMs,
                    correctCount = counts.correctCount,
                    wrongCount = counts.wrongCount,
                    seed = race.seed
                )
                raceMapper.toResponse(raceResultRepo.save(result), shownKey(race.answerKey, wrongSeqs))
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

    /** Another user's race, or one under another course, is not found: it is not the caller's to see. */
    private fun findRace(courseId: String, raceId: String, userId: String): Race {
        return raceRepo.findByIdAndUserId(raceId, userId)?.takeIf { it.courseId == courseId }
            ?: throw ResourceNotFoundException("That race was not found.")
    }

    private fun conflict(message: String) = ResponseStatusException(HttpStatus.CONFLICT, message)

    private fun requireCourse(courseId: String) {
        if (!courseRepo.existsById(courseId)) {
            throw ResourceNotFoundException("Course not found with id: $courseId")
        }
    }

    private fun requireEnrolled(courseId: String, userId: String) {
        requireCourse(courseId)
        if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
            throw UnauthorizedException(NOT_ENROLLED)
        }
    }

    private companion object {
        /** The `choice` that a timeout stands for: it matches no key, so it counts as wrong. */
        const val TIMEOUT = -1
        const val NOT_ENROLLED = "You need an active enrollment in this course to race"
        const val NOT_IN_RACE = "That question is not part of this race."
        const val RACE_OVER = "This race is already finished."
        val JSON = jacksonObjectMapper()
    }
}
