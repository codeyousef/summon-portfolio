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
            contact = request.contact.trim(),
            message = request.message.trim(),
            createdAt = Instant.now()
        )
        repository.save(submission)
        return Result.Success(submission)
    }

    fun list(): List<ContactSubmission> = repository.list()

    private fun ContactRequest.validate(): String? {
        if (contact.isBlank()) return "Contact info is required"
        if (message.isBlank()) return "Message is required"
        return null
    }

    sealed interface Result {
        data class Success(val submission: ContactSubmission) : Result
        data class Error(val reason: String) : Result
    }
}
