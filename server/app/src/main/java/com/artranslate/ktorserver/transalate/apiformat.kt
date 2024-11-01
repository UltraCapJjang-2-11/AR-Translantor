package com.artranslate.ktorserver.transalate

import kotlinx.serialization.Serializable

@Serializable
data class TranslationRequest(
    val q: List<String>,
    val target: String,
    val source: String? = null,
    val format: String? = "text",
    val model: String? = null
)

@Serializable
data class TranslationResponse(
    val data: TranslationsData
)

@Serializable
data class TranslationsData(
    val translations: List<Translation>
)

@Serializable
data class Translation(
    val translatedText: String
)
