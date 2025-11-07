package code.yousef.portfolio.contact

import java.time.Instant
import java.util.UUID

class ContactService(
    private val repository: ContactRepository = InMemoryContactRepository()
) {
    fun submit(request: ContactRequest): Result {
        request.validate()?.let { return Result.Error(it) }
        val submission = ContactSubmission(
            id = UUID.randomUUID().toString(),
            name = request.name.trim(),
            email = request.email?.trim().takeIf { !it.isNullOrBlank() },
            whatsapp = request.whatsapp.trim(),
            requirements = request.requirements.trim(),
            createdAt = Instant.now()
        )
        repository.save(submission)
        return Result.Success(submission)
    }

    private fun ContactRequest.validate(): String? {
        if (name.isBlank()) return "Name is required"
        if (whatsapp.isBlank()) return "WhatsApp number is required"
        if (requirements.isBlank()) return "Requirements are required"
        if (!email.isNullOrBlank() && !email.contains("@")) return "Invalid email"
        return null
    }

    sealed interface Result {
        data class Success(val submission: ContactSubmission) : Result
        data class Error(val reason: String) : Result
    }
}
