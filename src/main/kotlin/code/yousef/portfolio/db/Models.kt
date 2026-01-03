package code.yousef.portfolio.db

import codes.yousef.aether.db.*

object Projects : Model<ProjectEntity>() {
    override val tableName = "projects"
    override fun createInstance() = ProjectEntity()
    val id = varchar("id", primaryKey = true)
    val slug = varchar("slug")
    val title = text("title") // JSON
    val description = text("description") // JSON
    val category = varchar("category")
    val featured = boolean("featured")
    val order = integer("order")
    val technologies = text("technologies") // CSV
    val imageUrl = varchar("imageUrl", nullable = true)
    val githubUrl = varchar("githubUrl", nullable = true)
}

class ProjectEntity : BaseEntity<ProjectEntity>() {
    override fun getModel() = Projects
    
    var id by Projects.id
    var slug by Projects.slug
    var title by Projects.title
    var description by Projects.description
    var category by Projects.category
    var featured by Projects.featured
    var order by Projects.order
    var technologies by Projects.technologies
    var imageUrl by Projects.imageUrl
    var githubUrl by Projects.githubUrl
}

object Services : Model<ServiceEntity>() {
    override val tableName = "services"
    override fun createInstance() = ServiceEntity()
    val id = varchar("id", primaryKey = true)
    val title = text("title") // JSON
    val description = text("description") // JSON
    val featured = boolean("featured")
    val order = integer("order")
}

class ServiceEntity : BaseEntity<ServiceEntity>() {
    override fun getModel() = Services
    
    var id by Services.id
    var title by Services.title
    var description by Services.description
    var featured by Services.featured
    var order by Services.order
}

object BlogPosts : Model<BlogPostEntity>() {
    override val tableName = "blog_posts"
    override fun createInstance() = BlogPostEntity()
    val id = varchar("id", primaryKey = true)
    val slug = varchar("slug")
    val title = text("title")
    val excerpt = text("excerpt")
    val content = text("content")
    val publishedAt = varchar("publishedAt")
    val featured = boolean("featured")
    val author = varchar("author")
    val tags = text("tags") // CSV
}

class BlogPostEntity : BaseEntity<BlogPostEntity>() {
    override fun getModel() = BlogPosts
    
    var id by BlogPosts.id
    var slug by BlogPosts.slug
    var title by BlogPosts.title
    var excerpt by BlogPosts.excerpt
    var content by BlogPosts.content
    var publishedAt by BlogPosts.publishedAt
    var featured by BlogPosts.featured
    var author by BlogPosts.author
    var tags by BlogPosts.tags
}

object Testimonials : Model<TestimonialEntity>() {
    override val tableName = "testimonials"
    override fun createInstance() = TestimonialEntity()
    val id = varchar("id", primaryKey = true)
    val quote = text("quote") // JSON
    val author = varchar("author")
    val role = text("role") // JSON
    val company = text("company") // JSON
    val featured = boolean("featured")
    val order = integer("order")
}

class TestimonialEntity : BaseEntity<TestimonialEntity>() {
    override fun getModel() = Testimonials
    
    var id by Testimonials.id
    var quote by Testimonials.quote
    var author by Testimonials.author
    var role by Testimonials.role
    var company by Testimonials.company
    var featured by Testimonials.featured
    var order by Testimonials.order
}
