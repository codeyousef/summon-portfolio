package code.yousef.portfolio.content.repo

import code.yousef.portfolio.content.model.Service

interface ServiceRepository {
    fun list(): List<Service>
}
