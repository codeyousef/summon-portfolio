package code.yousef.portfolio.db

import code.yousef.portfolio.content.ContentStore
import code.yousef.portfolio.content.model.*
import codes.yousef.aether.db.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class MapRow(private val data: Map<String, Any?>) : Row {
    override fun getString(column: String): String? = data[column]?.toString()
    override fun getInt(column: String): Int? = (data[column] as? Number)?.toInt() ?: data[column]?.toString()?.toIntOrNull()
    override fun getLong(column: String): Long? = (data[column] as? Number)?.toLong() ?: data[column]?.toString()?.toLongOrNull()
    override fun getDouble(column: String): Double? = (data[column] as? Number)?.toDouble() ?: data[column]?.toString()?.toDoubleOrNull()
    override fun getBoolean(column: String): Boolean? = data[column] as? Boolean ?: data[column]?.toString()?.toBooleanStrictOrNull()
    override fun getValue(column: String): Any? = data[column]
    override fun getColumnNames(): List<String> = data.keys.toList()
    override fun hasColumn(column: String): Boolean = data.containsKey(column)
}

class ContentStoreDriver(private val store: ContentStore) : DatabaseDriver {

    override suspend fun executeQuery(query: QueryAST): List<Row> {
        if (query !is SelectQuery) throw DatabaseException("Only SelectQuery supported in executeQuery")
        
        val table = query.from
        val allRows = when (table) {
            "projects" -> store.listProjects().map { it.toMap() }
            "services" -> store.listServices().map { it.toMap() }
            "blog_posts" -> store.listBlogPosts().map { it.toMap() }
            "testimonials" -> store.listTestimonials().map { it.toMap() }
            else -> emptyList()
        }

        var filtered = if (query.where != null) {
            allRows.filter { row -> evaluate(query.where!!, row) }
        } else {
            allRows
        }

        // Order By
        if (query.orderBy.isNotEmpty()) {
            // Simple single column sort for now
            val order = query.orderBy.first()
            val col = (order.expression as? Expression.ColumnRef)?.column
            if (col != null) {
                filtered = filtered.sortedWith { a, b ->
                    val valA = a[col] as? Comparable<Any>
                    val valB = b[col] as? Comparable<Any>
                    if (valA == null || valB == null) 0
                    else if (order.direction == OrderDirection.ASC) valA.compareTo(valB)
                    else valB.compareTo(valA)
                }
            }
        }

        // Limit/Offset
        if (query.offset != null) {
            filtered = filtered.drop(query.offset!!)
        }
        if (query.limit != null) {
            filtered = filtered.take(query.limit!!)
        }

        return filtered.map { MapRow(it) }
    }

    override suspend fun executeQueryRaw(sql: String): List<Row> {
        throw DatabaseException("Raw SQL not supported")
    }

    override suspend fun executeUpdate(query: QueryAST): Int {
        return when (query) {
            is InsertQuery -> handleInsert(query)
            is UpdateQuery -> handleUpdate(query)
            is DeleteQuery -> handleDelete(query)
            else -> 0
        }
    }

    private suspend fun handleInsert(query: InsertQuery): Int {
        val values = query.columns.zip(query.values).associate { (col, expr) ->
            col to expr.toValue()
        }
        
        when (query.table) {
            "projects" -> store.upsertProject(values.toProject())
            "services" -> store.upsertService(values.toService())
            // Add others
        }
        return 1
    }

    private suspend fun handleUpdate(query: UpdateQuery): Int {
        // This is tricky because we need to know WHICH item to update.
        // Usually update has a WHERE clause on ID.
        val where = query.where
        if (where is WhereClause.Condition && 
            (where.left as? Expression.ColumnRef)?.column == "id" && 
            where.operator == ComparisonOperator.EQUALS) {
            
            val id = (where.right as? Expression.Literal)?.value?.toStringValue() ?: return 0
            
            // Fetch existing, apply updates, save
            // For simplicity, we assume full object update or we fetch-modify-save
            // But ContentStore only has upsert.
            
            // We need to fetch the existing item first to merge updates?
            // Or assume the update contains all fields?
            // Aether Admin forms usually submit all fields.
            
            // Let's try to fetch existing row first
            val existingRows = executeQuery(SelectQuery(listOf(Expression.Star), query.table, where = where))
            if (existingRows.isEmpty()) return 0
            
            val existingRow = (existingRows.first() as MapRow).toMap() // We need to expose map
            
            val updates = query.assignments.mapValues { it.value.toValue() }
            val merged = existingRow + updates
            
            when (query.table) {
                "projects" -> store.upsertProject(merged.toProject())
                "services" -> store.upsertService(merged.toService())
            }
            return 1
        }
        return 0
    }

    private suspend fun handleDelete(query: DeleteQuery): Int {
        val where = query.where
        if (where is WhereClause.Condition && 
            (where.left as? Expression.ColumnRef)?.column == "id" && 
            where.operator == ComparisonOperator.EQUALS) {
            
            val id = (where.right as? Expression.Literal)?.value?.toStringValue() ?: return 0
            
            when (query.table) {
                "projects" -> store.deleteProject(id)
                "services" -> store.deleteService(id)
                "blog_posts" -> store.deleteBlogPost(id)
                "testimonials" -> store.deleteTestimonial(id)
            }
            return 1
        }
        return 0
    }

    override suspend fun executeDDL(query: QueryAST) {
        // No-op
    }

    override suspend fun getTables(): List<String> {
        return listOf("projects", "services", "blog_posts", "testimonials")
    }

    override suspend fun getColumns(table: String): List<ColumnDefinition> {
        // Return dummy definitions or accurate ones if needed by Admin
        // Admin uses Model.columns, so this might not be called often
        return emptyList()
    }

    override suspend fun execute(sql: String, params: List<SqlValue>): Int {
        throw DatabaseException("Raw SQL not supported")
    }

    override suspend fun close() {
        // No-op
    }

    // Helpers
    private fun evaluate(where: WhereClause, row: Map<String, Any?>): Boolean {
        return when (where) {
            is WhereClause.Condition -> {
                val left = evaluateExpression(where.left, row)
                val right = evaluateExpression(where.right, row)
                compare(left, right, where.operator)
            }
            is WhereClause.And -> where.conditions.all { evaluate(it, row) }
            is WhereClause.Or -> where.conditions.any { evaluate(it, row) }
            is WhereClause.Like -> {
                val left = evaluateExpression(where.column, row)?.toString() ?: ""
                val pattern = evaluateExpression(where.pattern, row)?.toString() ?: ""
                // Simple regex for LIKE %...%
                val regex = pattern.replace("%", ".*").toRegex(RegexOption.IGNORE_CASE)
                regex.matches(left)
            }
            else -> true
        }
    }

    private fun evaluateExpression(expr: Expression, row: Map<String, Any?>): Any? {
        return when (expr) {
            is Expression.ColumnRef -> row[expr.column]
            is Expression.Literal -> expr.value.toValue()
            else -> null
        }
    }

    private fun compare(left: Any?, right: Any?, op: ComparisonOperator): Boolean {
        if (left == null || right == null) return left == right
        return when (op) {
            ComparisonOperator.EQUALS -> left.toString() == right.toString()
            ComparisonOperator.NOT_EQUALS -> left.toString() != right.toString()
            // Add others if needed
            else -> false
        }
    }

    private fun Expression.toValue(): Any? {
        return when (this) {
            is Expression.Literal -> this.value.toValue()
            else -> null
        }
    }

    private fun SqlValue.toValue(): Any? {
        return when (this) {
            is SqlValue.StringValue -> this.value
            is SqlValue.IntValue -> this.value
            is SqlValue.LongValue -> this.value
            is SqlValue.DoubleValue -> this.value
            is SqlValue.BooleanValue -> this.value
            is SqlValue.NullValue -> null
        }
    }
    
    private fun SqlValue.toStringValue(): String? = toValue()?.toString()

    // Mappers
    private fun Project.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "slug" to slug,
        "title" to Json.encodeToString(title), // Store as JSON string
        "description" to Json.encodeToString(description),
        "category" to category.name,
        "featured" to featured,
        "order" to order,
        "technologies" to technologies.joinToString(","), // Simple CSV for now
        "imageUrl" to imageUrl,
        "githubUrl" to githubUrl
    )

    private fun Map<String, Any?>.toProject(): Project {
        return Project(
            id = this["id"] as? String ?: "",
            slug = this["slug"] as? String ?: "",
            layerLabel = code.yousef.portfolio.i18n.LocalizedText("",""), // Dummy
            layerName = code.yousef.portfolio.i18n.LocalizedText("",""), // Dummy
            title = (this["title"] as? String)?.let { Json.decodeFromString(it) } ?: code.yousef.portfolio.i18n.LocalizedText("",""),
            description = (this["description"] as? String)?.let { Json.decodeFromString(it) } ?: code.yousef.portfolio.i18n.LocalizedText("",""),
            category = ProjectCategory.valueOf(this["category"] as? String ?: "WEB"),
            featured = this["featured"] as? Boolean ?: false,
            order = (this["order"] as? Number)?.toInt() ?: 0,
            technologies = (this["technologies"] as? String)?.split(",") ?: emptyList(),
            imageUrl = this["imageUrl"] as? String,
            githubUrl = this["githubUrl"] as? String
        )
    }
    
    private fun Service.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to Json.encodeToString(title),
        "description" to Json.encodeToString(description),
        "featured" to featured,
        "order" to order
    )

    private fun Map<String, Any?>.toService(): Service {
        return Service(
            id = this["id"] as? String ?: "",
            title = (this["title"] as? String)?.let { Json.decodeFromString(it) } ?: code.yousef.portfolio.i18n.LocalizedText("",""),
            description = (this["description"] as? String)?.let { Json.decodeFromString(it) } ?: code.yousef.portfolio.i18n.LocalizedText("",""),
            featured = this["featured"] as? Boolean ?: false,
            order = (this["order"] as? Number)?.toInt() ?: 0
        )
    }
    
    private fun BlogPost.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "slug" to slug,
        "title" to Json.encodeToString(title),
        "excerpt" to Json.encodeToString(excerpt),
        "content" to Json.encodeToString(content),
        "publishedAt" to publishedAt.toString(),
        "featured" to featured,
        "author" to author,
        "tags" to tags.joinToString(",")
    )
    
    private fun Testimonial.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "quote" to Json.encodeToString(quote),
        "author" to author,
        "role" to Json.encodeToString(role),
        "company" to Json.encodeToString(company),
        "featured" to featured,
        "order" to order
    )
    
    // Helper to expose map from MapRow
    private fun MapRow.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (col in this.getColumnNames()) {
            map[col] = this.getValue(col)
        }
        return map
    }
}
