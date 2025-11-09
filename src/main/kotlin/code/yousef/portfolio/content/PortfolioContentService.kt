package code.yousef.portfolio.content

import code.yousef.portfolio.content.repo.*
import code.yousef.portfolio.content.seed.PortfolioContentSeed
import code.yousef.portfolio.content.store.FileContentStore
import code.yousef.portfolio.content.store.StoreBlogRepository
import code.yousef.portfolio.content.store.StoreProjectRepository
import code.yousef.portfolio.content.store.StoreServiceRepository

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
        fun default(store: FileContentStore = FileContentStore.fromEnvironment()): PortfolioContentService =
            PortfolioContentService(
                heroProvider = { HeroProviderResult(store.snapshot().hero) },
                projectRepository = StoreProjectRepository(store),
                serviceRepository = StoreServiceRepository(store),
                blogRepository = StoreBlogRepository(store)
            )
    }
}
