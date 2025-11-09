package code.yousef.portfolio.admin

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.store.FileContentStore

class AdminContentService(private val store: FileContentStore) {
    fun saveProject(project: Project) = store.upsertProject(project)

    fun deleteProject(id: String) = store.deleteProject(id)

    fun saveService(service: Service) = store.upsertService(service)

    fun deleteService(id: String) = store.deleteService(id)

    fun saveBlogPost(post: BlogPost) = store.upsertBlogPost(post)

    fun deleteBlogPost(id: String) = store.deleteBlogPost(id)
}
