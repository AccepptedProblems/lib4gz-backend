package com.example.lib4gz.common.config

import com.example.lib4gz.auth.job.RegistrationCleanupJob
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * The API reference is generated from the controllers (springdoc), and it is the only one: no
 * hand-written OpenAPI file exists to drift. This keeps the generated document honest.
 *
 * No database, for the reason given in `Lib4gzApplicationTests`.
 */
@SpringBootTest(
    properties = [
        "springdoc.api-docs.enabled=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
    ]
)
@AutoConfigureMockMvc
class OpenApiDocumentTest {

    // The real job sweeps at startup: it would open a connection and delete expired rows in that database.
    @MockitoBean
    private lateinit var registrationCleanupJob: RegistrationCleanupJob

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    private val document by lazy {
        val body = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        ObjectMapper().readTree(body)
    }

    @Test
    fun `it is readable without a token`() {
        assertThat(document["openapi"].asText()).startsWith("3.")
    }

    @Test
    fun `every endpoint of every controller is in it`() {
        val served = handlerMapping.handlerMethods
            .filter { (_, method) -> method.beanType.packageName.startsWith("com.example.lib4gz") }
            .flatMap { (info, _) ->
                info.patternValues.flatMap { path -> info.methodsCondition.methods.map { "$it $path" } }
            }
            .toSet()

        val documented = document["paths"].properties()
            .flatMap { (path, operations) -> operations.fieldNames().asSequence().map { "${it.uppercase()} $path" } }
            .toSet()

        assertThat(served).isNotEmpty
        assertThat(documented).containsAll(served)
    }

    @Test
    fun `an operation asks for the access_token header, never for X-User-Id`() {
        val operations = document["paths"].flatMap { it.toList() }
        val headerNames = operations.flatMap { it["parameters"]?.toList().orEmpty() }.map { it["name"].asText() }

        assertThat(headerNames).doesNotContain("X-User-Id")
        assertThat(document["paths"]["/v1/users/me"]["get"]["security"].toString()).contains("access_token")
        assertThat(document["paths"]["/v1/auth/login"]["post"].has("security")).isFalse()
    }

    @Test
    fun `responses are described by their payload, not by the Mono or Flux around it`() {
        val schemas = document["components"]["schemas"].fieldNames().asSequence().toList()

        assertThat(schemas).contains("LoginResponse", "CourseResponse", "SubmissionResponse")
        assertThat(schemas).noneMatch { it.startsWith("Mono") || it.startsWith("Flux") }
    }
}
