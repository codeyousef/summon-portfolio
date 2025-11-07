package code.yousef.portfolio.content

import code.yousef.portfolio.content.repo.*
import code.yousef.portfolio.content.seed.PortfolioContentSeed

class PortfolioContentService(
    private val heroProvider: () -> HeroProviderResult,
    private val projectRepository: ProjectRepository,
    private val serviceRepository: ServiceRepository,
    private val blogRepository: BlogRepository
) {

    data class HeroProviderResult(val hero: code.yousef.portfolio.content.model.HeroContent)

    fun load(): PortfolioContent =
        PortfolioContent(
            hero = heroProvider().hero,
            projects = projectRepository.list(),
            services = serviceRepository.list(),
            blogPosts = blogRepository.list()
        )

    companion object {
        fun default(): PortfolioContentService =
            PortfolioContentService(
                heroProvider = { HeroProviderResult(PortfolioContentSeed.hero) },
                projectRepository = StaticProjectRepository(),
                serviceRepository = StaticServiceRepository(),
                blogRepository = StaticBlogRepository()
            )
    }
}
