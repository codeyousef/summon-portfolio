package code.yousef.portfolio.content

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial
import kotlinx.serialization.Serializable

@Serializable
data class PortfolioContent(
    val hero: HeroContent,
    val projects: List<Project>,
    val services: List<Service>,
    val blogPosts: List<BlogPost>,
    val testimonials: List<Testimonial> = emptyList()
)
