package com.example.lib4gz.common.utils

import com.github.f4b6a3.uuid.UuidCreator
import java.security.SecureRandom
import java.util.UUID

object IdGenerator {

    private val secureRandom = SecureRandom()

    fun generateNumericCode(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(secureRandom.nextInt(10))
        }
        return sb.toString()
    }

    private val CROCKFORD_ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz".toCharArray()

    fun generate(prefix: String): String {
        val uuid = UuidCreator.getTimeOrderedEpoch()
        val base32 = encodeBase32(uuid)
        return "${prefix}_${base32}"
    }

    private fun encodeBase32(uuid: UUID): String {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits

        val chars = CharArray(26)
        // Encode 128 bits (16 bytes) into 26 base32 characters (130 bits capacity, 2 bits padding)
        chars[0] = CROCKFORD_ALPHABET[((msb ushr 61) and 0x1F).toInt()]
        chars[1] = CROCKFORD_ALPHABET[((msb ushr 56) and 0x1F).toInt()]
        chars[2] = CROCKFORD_ALPHABET[((msb ushr 51) and 0x1F).toInt()]
        chars[3] = CROCKFORD_ALPHABET[((msb ushr 46) and 0x1F).toInt()]
        chars[4] = CROCKFORD_ALPHABET[((msb ushr 41) and 0x1F).toInt()]
        chars[5] = CROCKFORD_ALPHABET[((msb ushr 36) and 0x1F).toInt()]
        chars[6] = CROCKFORD_ALPHABET[((msb ushr 31) and 0x1F).toInt()]
        chars[7] = CROCKFORD_ALPHABET[((msb ushr 26) and 0x1F).toInt()]
        chars[8] = CROCKFORD_ALPHABET[((msb ushr 21) and 0x1F).toInt()]
        chars[9] = CROCKFORD_ALPHABET[((msb ushr 16) and 0x1F).toInt()]
        chars[10] = CROCKFORD_ALPHABET[((msb ushr 11) and 0x1F).toInt()]
        chars[11] = CROCKFORD_ALPHABET[((msb ushr 6) and 0x1F).toInt()]
        chars[12] = CROCKFORD_ALPHABET[((msb ushr 1) and 0x1F).toInt()]
        // Bit 0 of msb + bits 63-60 of lsb
        chars[13] = CROCKFORD_ALPHABET[(((msb and 0x1) shl 4) or ((lsb ushr 60) and 0xF)).toInt()]
        chars[14] = CROCKFORD_ALPHABET[((lsb ushr 55) and 0x1F).toInt()]
        chars[15] = CROCKFORD_ALPHABET[((lsb ushr 50) and 0x1F).toInt()]
        chars[16] = CROCKFORD_ALPHABET[((lsb ushr 45) and 0x1F).toInt()]
        chars[17] = CROCKFORD_ALPHABET[((lsb ushr 40) and 0x1F).toInt()]
        chars[18] = CROCKFORD_ALPHABET[((lsb ushr 35) and 0x1F).toInt()]
        chars[19] = CROCKFORD_ALPHABET[((lsb ushr 30) and 0x1F).toInt()]
        chars[20] = CROCKFORD_ALPHABET[((lsb ushr 25) and 0x1F).toInt()]
        chars[21] = CROCKFORD_ALPHABET[((lsb ushr 20) and 0x1F).toInt()]
        chars[22] = CROCKFORD_ALPHABET[((lsb ushr 15) and 0x1F).toInt()]
        chars[23] = CROCKFORD_ALPHABET[((lsb ushr 10) and 0x1F).toInt()]
        chars[24] = CROCKFORD_ALPHABET[((lsb ushr 5) and 0x1F).toInt()]
        chars[25] = CROCKFORD_ALPHABET[(lsb and 0x1F).toInt()]

        return String(chars)
    }
}
