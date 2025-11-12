package code.yousef.portfolio.contact

import java.time.Instant
import java.util.*

class ContactService(
    private val repository: ContactRepository = InMemoryContactRepository()
) {
    fun submit(request: ContactRequest): Result {
        request.validate()?.let { return Result.Error(it) }
        val submission = ContactSubmission(
            id = UUID.randomUUID().toString(),
            name = request.name.trim(),
            email = request.email?.trim()?.takeIf { it.isNotBlank() },
            whatsapp = request.whatsapp?.trim()?.takeIf { it.isNotBlank() },
            requirements = request.requirements.trim(),
            createdAt = Instant.now()
        )
        repository.save(submission)
        return Result.Success(submission)
    }

    fun list(): List<ContactSubmission> = repository.list()

    private fun ContactRequest.validate(): String? {
        val normalizedEmail = email?.trim().orEmpty()
        val normalizedWhatsapp = whatsapp?.trim().orEmpty()
        if (name.isBlank()) return "Name is required"
        if (requirements.isBlank()) return "Project details are required"
        if (normalizedEmail.isBlank() && normalizedWhatsapp.isBlank()) {
            return "Provide at least an email or WhatsApp number"
        }
        if (normalizedEmail.isNotBlank() && !normalizedEmail.contains("@")) return "Invalid email"
        return null
    }

    sealed interface Result {
        data class Success(val submission: ContactSubmission) : Result
        data class Error(val reason: String) : Result
    }
}
