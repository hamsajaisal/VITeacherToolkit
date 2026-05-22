package com.viteacher.toolkit.util

import java.security.MessageDigest

object SecurityHelper {

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(enteredPin: String, storedValue: String): Boolean {
        // If stored value is exactly 4 digits it is plain text — migrate it
        if (storedValue.length == 4 && storedValue.all { it.isDigit() }) {
            return enteredPin == storedValue
        }
        // Otherwise it is hashed — verify with hash
        return hashPin(enteredPin) == storedValue
    }

    fun isHashed(storedValue: String): Boolean {
        return !(storedValue.length == 4 && storedValue.all { it.isDigit() })
    }
}