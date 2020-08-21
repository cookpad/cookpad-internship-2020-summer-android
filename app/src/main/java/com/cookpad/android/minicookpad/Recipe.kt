package com.cookpad.android.minicookpad

import com.google.firebase.firestore.DocumentSnapshot
import java.security.SecureRandom

data class Recipe(
    val id: String = generateId(),
    val title: String,
    val imagePath: String?,
    val steps: List<String>,
    val authorName: String
) {
    fun toMap(): Map<String, Any?> = hashMapOf(
        "title" to title,
        "imagePath" to imagePath,
        "steps" to steps,
        "authorName" to authorName
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): Recipe? {
            return try {
                val steps = (document["steps"] as? List<*>)
                    ?.filterIsInstance<String>()
                    ?: emptyList()

                Recipe(
                    id = document.id,
                    title = document["title"] as String,
                    imagePath = document["imagePath"] as? String,
                    steps = steps,
                    authorName = document["authorName"] as String
                )
            } catch (e: Exception) {
                null
            }
        }

        private const val ID_PATTERN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val ID_LENGTH = 20

        private fun generateId(): String {
            val rand = SecureRandom()
            val sb = StringBuilder().apply {
                repeat(ID_LENGTH) { append(ID_PATTERN[rand.nextInt(ID_PATTERN.length)]) }
            }
            return sb.toString()
        }
    }
}
