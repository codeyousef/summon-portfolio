package code.yousef.portfolio.admin

import code.yousef.portfolio.content.ContentStore
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial

class AdminContentService(private val store: ContentStore) {
    fun saveProject(project: Project) = store.upsertProject(project)

    fun deleteProject(id: String) = store.deleteProject(id)

    fun saveService(service: Service) = store.upsertService(service)

    fun deleteService(id: String) = store.deleteService(id)

    fun saveBlogPost(post: BlogPost) = store.upsertBlogPost(post)

    fun deleteBlogPost(id: String) = store.deleteBlogPost(id)

    fun saveTestimonial(testimonial: Testimonial) = store.upsertTestimonial(testimonial)

    fun deleteTestimonial(id: String) = store.deleteTestimonial(id)
}
