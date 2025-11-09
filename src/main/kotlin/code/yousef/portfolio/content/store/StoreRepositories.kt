package code.yousef.portfolio.content.store

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.repo.BlogRepository
import code.yousef.portfolio.content.repo.ProjectRepository
import code.yousef.portfolio.content.repo.ServiceRepository

class StoreProjectRepository(private val store: FileContentStore) : ProjectRepository {
    override fun list(): List<Project> = store.snapshot().projects
}

class StoreServiceRepository(private val store: FileContentStore) : ServiceRepository {
    override fun list(): List<Service> = store.snapshot().services
}

class StoreBlogRepository(private val store: FileContentStore) : BlogRepository {
    override fun list(): List<BlogPost> = store.snapshot().blogPosts

    override fun findBySlug(slug: String): BlogPost? =
        list().firstOrNull { it.slug.equals(slug, ignoreCase = true) }
}
