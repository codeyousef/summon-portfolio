package code.yousef.portfolio.content

import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.BlogPost

data class PortfolioContent(
    val hero: HeroContent,
    val projects: List<Project>,
    val services: List<Service>,
    val blogPosts: List<BlogPost>
)
