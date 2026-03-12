package com.example.lib4gz.exercise.service

import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.exercise.model.entity.StudentAnswer
import com.example.lib4gz.exercise.model.entity.Submission
import com.example.lib4gz.exercise.model.entity.SubmissionStatus
import com.example.lib4gz.exercise.model.mapper.SubmissionMapper
import com.example.lib4gz.exercise.model.payload.*
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.QuestionRepo
import com.example.lib4gz.exercise.repo.StudentAnswerRepo
import com.example.lib4gz.exercise.repo.SubmissionRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface SubmissionService {

    fun createOrUpdateSubmission(exerciseId: String, userId: String, request: CreateSubmissionRequest): Mono<SubmissionResponse>

    fun getSubmission(submissionId: String, userId: String): Mono<SubmissionResponse>

    fun getUserSubmissionForExercise(exerciseId: String, userId: String): Mono<SubmissionResponse>

    fun listExerciseSubmissions(exerciseId: String, userId: String): Flux<SubmissionResponse>

    fun submitForReview(submissionId: String, userId: String): Mono<SubmissionResponse>

    fun approveSubmission(submissionId: String, teacherId: String): Mono<SubmissionResponse>

    fun requestRevision(submissionId: String, teacherId: String, feedback: String?): Mono<SubmissionResponse>

    fun addTeacherComment(answerId: String, teacherId: String, comment: String): Mono<SubmissionResponse>
}

@Service
@Transactional
class SubmissionServiceImpl(
    private val submissionRepo: SubmissionRepo,
    private val studentAnswerRepo: StudentAnswerRepo,
    private val exerciseRepo: ExerciseRepo,
    private val questionRepo: QuestionRepo,
    private val userRepo: UserRepo,
    private val exerciseService: ExerciseService,
    private val enrollmentService: EnrollmentService,
    private val submissionMapper: SubmissionMapper
) : SubmissionService {

    override fun createOrUpdateSubmission(exerciseId: String, userId: String, request: CreateSubmissionRequest): Mono<SubmissionResponse> {
        return Mono.fromCallable {
            val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
                ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exerciseService.getCourseIdByExercise(exerciseId)

            // Only enrolled learners can create submissions
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You must be enrolled in the course to submit")
            }

            val user = userRepo.findById(userId).orElseThrow {
                ResourceNotFoundException("User not found with id: $userId")
            }

            // Find or create submission
            var submission = submissionRepo.findByExercise_IdAndUser_Id(exerciseId, userId)

            if (submission == null) {
                submission = Submission(
                    exercise = exercise,
                    user = user,
                    status = SubmissionStatus.DRAFT
                )
                submission = submissionRepo.save(submission)
            }

            // Update answers
            for (answerRequest in request.answers) {
                val question = questionRepo.findById(answerRequest.questionId).orElseThrow {
                    ResourceNotFoundException("Question not found with id: ${answerRequest.questionId}")
                }

                // Verify question belongs to this exercise
                if (question.exerciseId != exerciseId) {
                    throw BadRequestException("Question ${answerRequest.questionId} does not belong to exercise $exerciseId")
                }

                var studentAnswer = studentAnswerRepo.findBySubmission_IdAndQuestion_Id(submission.id, answerRequest.questionId)

                if (studentAnswer == null) {
                    studentAnswer = StudentAnswer(
                        question = question,
                        submission = submission,
                        answer = answerRequest.answer
                    )
                } else {
                    studentAnswer.answer = answerRequest.answer
                }

                studentAnswerRepo.save(studentAnswer)
            }

            buildSubmissionResponse(submission)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getSubmission(submissionId: String, userId: String): Mono<SubmissionResponse> {
        return Mono.fromCallable {
            val submission = submissionRepo.findById(submissionId).orElseThrow {
                ResourceNotFoundException("Submission not found with id: $submissionId")
            }

            val courseId = exerciseService.getCourseIdByExercise(submission.exercise.id)

            // Users can view their own submissions, teachers can view all
            val isSelf = submission.user.id == userId
            val isTeacher = enrollmentService.isTeacherInCourse(courseId, userId)

            if (!isSelf && !isTeacher) {
                throw UnauthorizedException("You do not have access to this submission")
            }

            buildSubmissionResponse(submission)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getUserSubmissionForExercise(exerciseId: String, userId: String): Mono<SubmissionResponse> {
        return Mono.fromCallable {
            val submission = submissionRepo.findByExercise_IdAndUser_Id(exerciseId, userId)
            submission?.let { buildSubmissionResponse(it) }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun listExerciseSubmissions(exerciseId: String, userId: String): Flux<SubmissionResponse> {
        return Mono.fromCallable {
            // Verify exercise exists
            if (!exerciseRepo.existsById(exerciseId)) {
                throw ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exerciseService.getCourseIdByExercise(exerciseId)

            // Check if user has access
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this exercise")
            }

            val isTeacher = enrollmentService.isTeacherInCourse(courseId, userId)

            // Teachers see all submissions, learners only see their own
            val submissions = if (isTeacher) {
                submissionRepo.findByExercise_Id(exerciseId)
            } else {
                val userSubmission = submissionRepo.findByExercise_IdAndUser_Id(exerciseId, userId)
                if (userSubmission != null) listOf(userSubmission) else emptyList()
            }

            submissions.map { buildSubmissionResponse(it) }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }

    override fun submitForReview(submissionId: String, userId: String): Mono<SubmissionResponse> {
        return Mono.fromCallable {
            val submission = submissionRepo.findById(submissionId).orElseThrow {
                ResourceNotFoundException("Submission not found with id: $submissionId")
            }

            // Only the owner can submit
            if (submission.user.id != userId) {
                throw UnauthorizedException("You can only submit your own submission")
            }

            try {
                submission.submit()
            } catch (e: IllegalArgumentException) {
                throw BadRequestException(e.message ?: "Invalid submission state")
            }

            val savedSubmission = submissionRepo.save(submission)
            buildSubmissionResponse(savedSubmission)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun approveSubmission(submissionId: String, teacherId: String): Mono<SubmissionResponse> {
        return Mono.fromCallable {
            val submission = submissionRepo.findById(submissionId).orElseThrow {
                ResourceNotFoundException("Submission not found with id: $submissionId")
            }

            val courseId = exerciseService.getCourseIdByExercise(submission.exercise.id)

            // Only teachers can approve
            if (!enrollmentService.isTeacherInCourse(courseId, teacherId)) {
                throw UnauthorizedException("Only teachers can approve submissions")
            }

            try {
                submission.approve()
            } catch (e: IllegalArgumentException) {
                throw BadRequestException(e.message ?: "Invalid submission state")
            }

            val savedSubmission = submissionRepo.save(submission)
            buildSubmissionResponse(savedSubmission)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun requestRevision(submissionId: String, teacherId: String, feedback: String?): Mono<SubmissionResponse> {
        return Mono.fromCallable {
            val submission = submissionRepo.findById(submissionId).orElseThrow {
                ResourceNotFoundException("Submission not found with id: $submissionId")
            }

            val courseId = exerciseService.getCourseIdByExercise(submission.exercise.id)

            // Only teachers can request revision
            if (!enrollmentService.isTeacherInCourse(courseId, teacherId)) {
                throw UnauthorizedException("Only teachers can request revisions")
            }

            try {
                submission.requestRevision()
            } catch (e: IllegalArgumentException) {
                throw BadRequestException(e.message ?: "Invalid submission state")
            }

            val savedSubmission = submissionRepo.save(submission)
            buildSubmissionResponse(savedSubmission)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun addTeacherComment(answerId: String, teacherId: String, comment: String): Mono<SubmissionResponse> {
        return Mono.fromCallable {
            val answer = studentAnswerRepo.findById(answerId).orElseThrow {
                ResourceNotFoundException("Answer not found with id: $answerId")
            }

            val courseId = exerciseService.getCourseIdByExercise(answer.submission.exercise.id)

            // Only teachers can add comments
            if (!enrollmentService.isTeacherInCourse(courseId, teacherId)) {
                throw UnauthorizedException("Only teachers can add comments")
            }

            answer.teacherComment = comment
            studentAnswerRepo.save(answer)

            buildSubmissionResponse(answer.submission)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun buildSubmissionResponse(submission: Submission): SubmissionResponse {
        val answers = studentAnswerRepo.findBySubmissionIdOrderedByQuestion(submission.id)
        val questionIds = answers.map { it.question.id }
        val questions = if (questionIds.isNotEmpty()) {
            questionRepo.findAllById(questionIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        return submissionMapper.toResponse(submission, answers, questions)
    }
}
