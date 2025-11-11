package code.yousef.firestore

import code.yousef.config.AppConfig
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions

object FirestoreProvider {
    fun create(config: AppConfig): Firestore {
        val builder = FirestoreOptions.getDefaultInstance().toBuilder()
            .setProjectId(config.projectId)
        if (config.emulatorHost != null) {
            builder.setEmulatorHost(config.emulatorHost)
        } else {
            val credentials = runCatching { GoogleCredentials.getApplicationDefault() }
                .getOrElse { throw IllegalStateException("Unable to load ADC credentials", it) }
            builder.setCredentials(credentials)
        }
        return builder.build().service
    }
}
