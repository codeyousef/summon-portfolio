package code.yousef.portfolio.contact

import java.util.concurrent.CopyOnWriteArrayList

class InMemoryContactRepository : ContactRepository {
    private val submissions = CopyOnWriteArrayList<ContactSubmission>()

    override fun save(submission: ContactSubmission) {
        submissions.add(submission)
    }

    override fun list(): List<ContactSubmission> = submissions.toList()
}
