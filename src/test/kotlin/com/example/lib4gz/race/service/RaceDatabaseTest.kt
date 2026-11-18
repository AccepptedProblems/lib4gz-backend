package com.example.lib4gz.race.service

import com.example.lib4gz.MigrationCheckGuard
import com.example.lib4gz.auth.job.RefreshTokenCleanupJob
import com.example.lib4gz.auth.job.RegistrationCleanupJob
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.race.model.entity.Race
import com.example.lib4gz.race.model.mapper.RaceMapper
import com.example.lib4gz.race.model.payload.AnswerRequest
import com.example.lib4gz.race.model.payload.FinishRaceRequest
import com.example.lib4gz.race.model.payload.StartRaceRequest
import com.example.lib4gz.race.repo.RaceQuestionRepo
import com.example.lib4gz.race.repo.RaceRepo
import com.example.lib4gz.race.repo.RaceResultRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import javax.sql.DataSource

/**
 * The answer and finish SQL against a real Postgres. Runs only inside the migration check, and only
 * when the resolved datasource is on localhost: it writes rows. See `Lib4gzApplicationTests`.
 */
@EnabledIfEnvironmentVariable(named = "MIGRATION_CHECK", matches = "true")
@Import(MigrationCheckGuard::class)
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=\${MIGRATION_CHECK:false}",
        "spring.jpa.hibernate.ddl-auto=\${MIGRATION_SCHEMA_CHECK:none}",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
    ]
)
class RaceDatabaseTest {

    @MockitoBean
    private lateinit var registrationCleanupJob: RegistrationCleanupJob

    @MockitoBean
    private lateinit var refreshTokenCleanupJob: RefreshTokenCleanupJob

    @Autowired private lateinit var raceService: RaceService
    @Autowired private lateinit var raceRepo: RaceRepo
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource

    // For the service built by hand in the last test.
    @Autowired private lateinit var raceQuestionRepo: RaceQuestionRepo
    @Autowired private lateinit var raceResultRepo: RaceResultRepo
    @Autowired private lateinit var courseRepo: CourseRepo
    @Autowired private lateinit var moduleRepo: ModuleRepo
    @Autowired private lateinit var userRepo: UserRepo
    @Autowired private lateinit var enrollmentService: EnrollmentService
    @Autowired private lateinit var transactionTemplate: TransactionTemplate
    @Autowired private lateinit var properties: RaceProperties

    @BeforeEach
    fun onlyOnThisMachine() {
        assumeTrue(MigrationCheckGuard.isLocal(MigrationCheckGuard.urlOf(dataSource)))
    }

    /** An owner (creator, so enrolled) and a stranger, a course with one module and three questions, and a dealt race of five. */
    private inner class Fixture {
        val tag = UUID.randomUUID().toString().take(8)
        val owner = "usr_o$tag"
        val stranger = "usr_s$tag"
        val course = "crs_$tag"
        val module = "mod_$tag"
        val raceId: String
        val key: List<Int>

        init {
            val now = System.currentTimeMillis()
            for (user in listOf(owner, stranger)) {
                jdbc.update(
                    "INSERT INTO users (id, username, name, email_verified, status, created_at, updated_at) VALUES (?, ?, ?, true, 'ACTIVE', ?, ?)",
                    user, user, user, now, now
                )
            }
            addCourse(course, tag)
            jdbc.update("INSERT INTO modules (id, course_id, title, order_index, created_at, updated_at) VALUES (?, ?, 'M', 0, ?, ?)", module, course, now, now)
            listOf(listOf("a", "b", "c", "d", "e", "f") to 5, listOf("x", "y") to 0, listOf("p", "q", "r") to 1).forEachIndexed { i, (options, key) ->
                jdbc.update(
                    "INSERT INTO race_questions (id, course_id, module_id, content, options, correct_index, order_index, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'Q', CAST(? AS jsonb), ?, ?, ?, ?)",
                    "rqn_$i$tag", course, module, options.joinToString(",", "[", "]") { """{"text":"$it"}""" }, key, i, now, now
                )
            }
            raceId = raceService.startRace(course, owner, StartRaceRequest(moduleId = module, questionCount = 5)).block()!!.raceId
            key = race().answerKey
        }

        fun addCourse(id: String, code: String) {
            val now = System.currentTimeMillis()
            jdbc.update(
                "INSERT INTO courses (id, code, title, visibility, created_by, created_at, updated_at) VALUES (?, ?, 'C', 'PRIVATE', ?, ?, ?)",
                id, code, owner, now, now
            )
        }

        fun race(): Race = raceRepo.findById(raceId).get()

        fun answer(seq: Int, choice: Int?, user: String = owner, courseId: String = course) =
            raceService.answer(courseId, raceId, user, AnswerRequest(seq, choice)).block()!!

        fun finish(ms: Int, user: String = owner, courseId: String = course) =
            raceService.finishRace(courseId, raceId, user, FinishRaceRequest(ms)).block()!!

        /** Moves the start back, so a finish of a few minutes is inside the wall time. */
        fun startedMinutesAgo(minutes: Int) {
            jdbc.update("UPDATE races SET started_at = ? WHERE id = ?", System.currentTimeMillis() - minutes * 60_000L, raceId)
        }

        fun wrong(seq: Int) = (key[seq] + 1) % 2
    }

    @Test
    fun `a right answer counts once and a resend gets the first verdict`() {
        val f = Fixture()
        assertEquals(true, f.answer(0, f.key[0]).correct)

        val resend = f.answer(0, f.wrong(0))

        assertEquals(true, resend.correct)
        assertEquals(1 to 0, resend.correctCount to resend.wrongCount)
        assertEquals(1, f.race().nextSeq)
    }

    @Test
    fun `a retry of an earlier question is refused`() {
        val f = Fixture()
        f.answer(0, f.wrong(0))
        f.answer(1, f.key[1])

        val retry = assertThrows(ResponseStatusException::class.java) { f.answer(0, f.key[0]) }

        assertEquals(HttpStatus.CONFLICT, retry.statusCode)
        assertEquals(1 to 1, f.race().correctCount to f.race().wrongCount)
    }

    @Test
    fun `a skipped question counts neither way and cannot be answered after`() {
        val f = Fixture()
        val reply = f.answer(2, f.key[2])

        assertEquals(1 to 0, reply.correctCount to reply.wrongCount)
        val late = assertThrows(ResponseStatusException::class.java) { f.answer(1, f.key[1]) }
        assertEquals("That question has already been answered or skipped.", late.reason)
    }

    @Test
    fun `a timeout counts as wrong`() {
        val f = Fixture()
        val reply = f.answer(0, null)

        assertEquals(false, reply.correct)
        assertEquals(0 to 1, reply.correctCount to reply.wrongCount)
    }

    @Test
    fun `a wrong answer is recorded and a right one is not`() {
        val f = Fixture()
        f.answer(0, f.key[0])
        assertEquals(emptyList<Int>(), f.race().wrongSeqs)

        f.answer(1, f.wrong(1))

        assertEquals(listOf(1), f.race().wrongSeqs)
    }

    @Test
    fun `a timeout is recorded as wrong`() {
        val f = Fixture()
        f.answer(0, null)

        assertEquals(listOf(0), f.race().wrongSeqs)
    }

    @Test
    fun `skipped numbers are not recorded`() {
        val wrongFirst = Fixture()
        wrongFirst.answer(3, wrongFirst.wrong(3))
        wrongFirst.answer(4, wrongFirst.key[4])
        assertEquals(listOf(3), wrongFirst.race().wrongSeqs)

        val rightFirst = Fixture()
        rightFirst.answer(3, rightFirst.key[3])
        assertEquals(emptyList<Int>(), rightFirst.race().wrongSeqs)
    }

    @Test
    fun `a refused resend or retry adds nothing`() {
        val f = Fixture()
        f.answer(0, f.wrong(0))
        f.answer(0, f.wrong(0))
        f.answer(1, f.key[1])

        assertThrows(ResponseStatusException::class.java) { f.answer(0, f.wrong(0)) }

        assertEquals(listOf(0), f.race().wrongSeqs)
    }

    @Test
    fun `wrong numbers stay in race order`() {
        val f = Fixture()
        f.answer(0, f.wrong(0))
        f.answer(1, f.key[1])
        f.answer(2, f.wrong(2))
        f.answer(3, null)
        f.answer(4, f.wrong(4))

        assertEquals(listOf(0, 2, 3, 4), f.race().wrongSeqs)
    }

    @Test
    fun `another user's race is refused and left as it was`() {
        val f = Fixture()

        assertThrows(ResourceNotFoundException::class.java) { f.answer(0, f.key[0], user = f.stranger) }
        assertEquals(0, f.race().nextSeq)
        f.startedMinutesAgo(10)
        assertThrows(ResourceNotFoundException::class.java) { f.finish(200_000, user = f.stranger) }
        assertEquals(null, f.race().finishedAt)
    }

    @Test
    fun `a race under another course is refused, even one the caller is enrolled in`() {
        val f = Fixture()
        val other = "crs_x${f.tag}"
        f.addCourse(other, "X${f.tag.take(6)}")

        assertThrows(ResourceNotFoundException::class.java) { f.answer(0, f.key[0], courseId = other) }
        assertEquals(0, f.race().nextSeq)
        f.startedMinutesAgo(10)
        assertThrows(ResourceNotFoundException::class.java) { f.finish(200_000, courseId = other) }
        assertEquals(null, f.race().finishedAt)
    }

    @Test
    fun `a question past the deck is refused`() {
        val f = Fixture()
        assertThrows(BadRequestException::class.java) { f.answer(5, 0) }
    }

    @Test
    fun `a finish is refused over the wall time and under the floor, saved once, then takes no answers`() {
        val f = Fixture()
        f.answer(0, f.key[0])
        f.answer(1, f.wrong(1))

        assertThrows(BadRequestException::class.java) { f.finish(200_000) }
        f.startedMinutesAgo(10)
        // One right answer with a 2641-unit lap: 180.07 s less 3.5 s.
        assertThrows(BadRequestException::class.java) { f.finish(176_000) }
        val result = f.finish(200_000)
        assertEquals(1 to 1, result.correctCount to result.wrongCount)
        assertEquals(listOf(null, f.key[1], null, null, null), result.answerKey)

        val again = assertThrows(ResponseStatusException::class.java) { f.finish(200_000) }
        assertEquals(HttpStatus.CONFLICT, again.statusCode)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM race_results WHERE user_id = ?", Int::class.java, f.owner))
        val late = assertThrows(ResponseStatusException::class.java) { f.answer(2, f.key[2]) }
        assertEquals("This race is already finished.", late.reason)
    }

    @Test
    fun `a finish sends the key of the first four wrong answers only`() {
        val f = Fixture()
        f.answer(0, f.wrong(0))
        f.answer(1, null)
        f.answer(2, f.wrong(2))
        f.answer(3, f.wrong(3))
        f.answer(4, f.wrong(4))
        f.startedMinutesAgo(10)

        val result = f.finish(200_000)

        assertEquals(0 to 5, result.correctCount to result.wrongCount)
        assertEquals(f.key.take(4) + listOf(null), result.answerKey)
    }

    @Test
    fun `a finish sends no key for right, skipped or unreached questions`() {
        val f = Fixture()
        f.answer(0, f.key[0])
        f.answer(2, f.wrong(2))
        f.startedMinutesAgo(10)

        val result = f.finish(200_000)

        assertEquals(listOf(null, null, f.key[2], null, null), result.answerKey)
    }

    @Test
    fun `an answer that lands between the finish's read and its finish mark is in the saved result and the key`() {
        val f = Fixture()
        f.answer(0, f.key[0])
        f.startedMinutesAgo(10)
        // The finish reads the race, then this wrong answer commits, then the finish marks the race finished.
        val answerInBetween = object : RaceRepo by raceRepo {
            override fun findByIdAndUserId(id: String, userId: String): Race? =
                raceRepo.findByIdAndUserId(id, userId).also { raceRepo.answer(id, userId, f.course, 1, f.wrong(1)) }
        }
        val service = RaceServiceImpl(
            raceQuestionRepo, raceResultRepo, answerInBetween, courseRepo, moduleRepo, userRepo,
            enrollmentService, RaceMapper(), transactionTemplate, properties
        )

        val result = service.finishRace(f.course, f.raceId, f.owner, FinishRaceRequest(200_000)).block()!!

        assertEquals(1 to 1, f.race().correctCount to f.race().wrongCount)
        assertEquals(1 to 1, result.correctCount to result.wrongCount)
        assertEquals(listOf(null, f.key[1], null, null, null), result.answerKey)
    }
}
