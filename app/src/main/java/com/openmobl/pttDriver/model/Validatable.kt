package com.openmobl.pttDriver.model

interface Validatable {
    val isValid: Boolean
    val validationErrors: List<String>?
    val allValidationErrors: Map<String, List<String>?>
}