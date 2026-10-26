package com.tarun.managemyshop.domain.model

sealed interface SaveResult<out T> {
    data class Success<T>(val value: T) : SaveResult<T>

    /** [errors] maps a field name (e.g. "name") to a user-facing message. */
    data class Invalid(val errors: Map<String, String>) : SaveResult<Nothing>
}
