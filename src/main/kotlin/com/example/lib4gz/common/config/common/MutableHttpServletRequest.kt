package com.example.lib4gz.common.config.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.*

class MutableHttpServletRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    private val customHeaders: MutableMap<String, String> = mutableMapOf()
    private val removedHeaders: MutableSet<String> = mutableSetOf()

    fun addHeader(name: String, value: String) {
        removedHeaders.remove(name)
        customHeaders[name] = value
    }

    fun removeHeader(name: String) {
        customHeaders.remove(name)
        removedHeaders.add(name)
    }

    override fun getHeader(name: String): String? {
        if (removedHeaders.contains(name)) return null
        return customHeaders[name] ?: super.getHeader(name)
    }

    override fun getHeaderNames(): Enumeration<String> {
        val names = mutableSetOf<String>()
        names.addAll(customHeaders.keys)
        super.getHeaderNames()?.let { parentNames ->
            while (parentNames.hasMoreElements()) {
                val name = parentNames.nextElement()
                if (!removedHeaders.contains(name)) {
                    names.add(name)
                }
            }
        }
        return Collections.enumeration(names)
    }

    override fun getHeaders(name: String): Enumeration<String> {
        if (removedHeaders.contains(name)) return Collections.emptyEnumeration()
        return customHeaders[name]?.let { Collections.enumeration(listOf(it)) }
            ?: super.getHeaders(name)
    }
}
