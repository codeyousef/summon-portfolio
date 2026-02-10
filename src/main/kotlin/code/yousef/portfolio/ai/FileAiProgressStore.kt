package code.yousef.portfolio.ai

import kotlinx.serialization.json.*
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FileAiProgressStore(
    private val path: Path = Path.of("storage/ai-progress.json")
) : AiProgressStore {

    private val lock = ReentrantLock()
    private val json = Json { prettyPrint = true }

    override suspend fun getProgress(): Map<String, Boolean> = lock.withLock {
        if (!path.exists()) return@withLock emptyMap()
        val text = path.readText().trim()
        if (text.isBlank()) return@withLock emptyMap()
        val obj = json.parseToJsonElement(text).jsonObject
        obj.mapValues { (_, v) -> v.jsonPrimitive.booleanOrNull ?: false }
    }

    override suspend fun updateProgress(subsectionId: String, completed: Boolean) = lock.withLock {
        path.parent?.createDirectories()
        val existing = if (path.exists()) {
            val text = path.readText().trim()
            if (text.isNotBlank()) json.parseToJsonElement(text).jsonObject else buildJsonObject {}
        } else {
            buildJsonObject {}
        }
        val updated = buildJsonObject {
            existing.forEach { (k, v) -> put(k, v) }
            put(subsectionId, completed)
        }
        path.writeText(json.encodeToString(JsonObject.serializer(), updated))
    }
}
