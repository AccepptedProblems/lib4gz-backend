package com.example.lib4gz.common.freshness

import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.springframework.stereotype.Component

/**
 * Hibernate does not discover event listeners from the Spring context — they must be
 * appended to the SessionFactory's EventListenerRegistry explicitly. Runs once at
 * startup, after the EntityManagerFactory exists.
 */
@Component
class FreshnessListenerRegistrar(
    private val entityManagerFactory: EntityManagerFactory,
    private val listener: FreshnessEntityListener
) {

    @PostConstruct
    fun register() {
        val sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor::class.java)
        val registry = requireNotNull(
            sessionFactory.serviceRegistry.getService(EventListenerRegistry::class.java)
        ) { "Hibernate EventListenerRegistry is unavailable; freshness bumps cannot be registered" }

        registry.appendListeners(EventType.POST_INSERT, listener)
        registry.appendListeners(EventType.POST_UPDATE, listener)
        registry.appendListeners(EventType.POST_DELETE, listener)
    }
}
