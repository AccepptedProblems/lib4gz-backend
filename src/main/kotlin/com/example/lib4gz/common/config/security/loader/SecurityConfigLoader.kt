package com.example.lib4gz.common.config.security.loader

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class AppSecurityConfigLoader {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @PostConstruct
    fun init() {
        loadConfig()
    }

    fun loadConfig(): SecurityConfigProperties {
        return try {
            val resource = ClassPathResource("security.json")
            objectMapper.readValue(resource.inputStream, SecurityConfigProperties::class.java)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load security configuration", e)
        }
    }
}