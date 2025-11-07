package code.yousef.portfolio.contact

interface ContactRepository {
    fun save(submission: ContactSubmission)
    fun list(): List<ContactSubmission>
}
