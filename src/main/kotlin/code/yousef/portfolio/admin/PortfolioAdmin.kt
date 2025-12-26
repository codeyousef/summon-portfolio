package code.yousef.portfolio.admin

import codes.yousef.aether.admin.AdminSite
import codes.yousef.aether.admin.ModelAdmin
import code.yousef.portfolio.db.*

fun createAdminSite(): AdminSite {
    val admin = AdminSite()
    
    admin.register(Projects, object : ModelAdmin<ProjectEntity>(Projects) {
        override val listDisplay = listOf("slug", "category", "featured", "order")
        override val searchFields = listOf("slug", "category")
        override val listFilter = listOf("category", "featured")
    })
    
    admin.register(Services, object : ModelAdmin<ServiceEntity>(Services) {
        override val listDisplay = listOf("id", "featured", "order")
    })
    
    admin.register(BlogPosts, object : ModelAdmin<BlogPostEntity>(BlogPosts) {
        override val listDisplay = listOf("slug", "publishedAt", "featured", "author")
        override val searchFields = listOf("slug", "author")
    })
    
    admin.register(Testimonials, object : ModelAdmin<TestimonialEntity>(Testimonials) {
        override val listDisplay = listOf("author", "featured", "order")
    })
    
    return admin
}
