package code.yousef.portfolio.content.repo

import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.seed.PortfolioContentSeed

class StaticProjectRepository : ProjectRepository {
    private val data: List<Project> = PortfolioContentSeed.projects

    override fun list(): List<Project> = data
}

class StaticServiceRepository : ServiceRepository {
    private val data: List<Service> = PortfolioContentSeed.services

    override fun list(): List<Service> = data
}

class StaticBlogRepository : BlogRepository {
    private val data: List<BlogPost> = PortfolioContentSeed.blogPosts

    override fun list(): List<BlogPost> = data

    override fun findBySlug(slug: String): BlogPost? =
        data.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
}
