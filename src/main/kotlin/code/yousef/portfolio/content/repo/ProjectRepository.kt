package code.yousef.portfolio.content.repo

import code.yousef.portfolio.content.model.Project

interface ProjectRepository {
    fun list(): List<Project>
}
