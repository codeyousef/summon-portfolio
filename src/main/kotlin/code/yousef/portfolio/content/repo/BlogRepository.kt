package code.yousef.portfolio.content.repo

import code.yousef.portfolio.content.model.BlogPost

interface BlogRepository {
    fun list(): List<BlogPost>
    fun findBySlug(slug: String): BlogPost?
}
