package code.yousef.portfolio.contact

import code.yousef.portfolio.content.ContentStore

interface ContactRepository {
    fun save(submission: ContactSubmission)
    fun list(): List<ContactSubmission>
}

class FileContactRepository(private val store: ContentStore) : ContactRepository {
    override fun save(submission: ContactSubmission) {
        store.upsertContactSubmission(submission)
    }

    override fun list(): List<ContactSubmission> = store.listContactSubmissions()
}
