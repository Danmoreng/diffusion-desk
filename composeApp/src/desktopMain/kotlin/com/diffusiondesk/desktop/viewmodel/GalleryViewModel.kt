package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.GalleryImage
import com.diffusiondesk.desktop.core.GalleryKeyword
import com.diffusiondesk.desktop.core.GalleryRepository
import com.diffusiondesk.desktop.core.GalleryReusableParams
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.ImageTaggingService
import com.diffusiondesk.desktop.core.LlmPresetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val keywords: List<String> = emptyList(),
    val keywordStats: List<GalleryKeyword> = emptyList(),
    val selectedImageId: Long? = null,
    val query: String = "",
    val selectedKeywords: List<String> = emptyList(),
    val keywordDraft: String = "",
    val isIndexing: Boolean = false,
    val isTaggingSelectedImage: Boolean = false,
    val isDeletingImage: Boolean = false,
    val message: String = "",
    val error: String? = null,
) {
    val selectedImage: GalleryImage? get() = images.firstOrNull { it.id == selectedImageId } ?: images.firstOrNull()
}

class GalleryViewModel(
    private val scope: CoroutineScope,
    private val repository: GalleryRepository,
    private val settingsStore: DesktopSettingsStore,
    private val llmPresetStore: LlmPresetStore,
    private val imageTaggingService: ImageTaggingService,
) {
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun refresh(outputDir: String) {
        scope.launch {
            update { copy(isIndexing = true, error = null, message = "Loading gallery...") }
            loadCachedList(keepIndexing = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val indexed = repository.indexOutputDirectory(outputDir)
                    val images = repository.listImages(_uiState.value.query, _uiState.value.selectedKeywords)
                    val keywordStats = repository.listKeywordStats()
                    Triple(indexed, images, keywordStats)
                }
            }.onSuccess { (indexed, images, keywordStats) ->
                update {
                    copy(
                        images = images,
                        keywords = keywordStats.filter { it.count > 0 }.map { it.name },
                        keywordStats = keywordStats,
                        selectedImageId = selectedImageId?.takeIf { id -> images.any { it.id == id } } ?: images.firstOrNull()?.id,
                        isIndexing = false,
                        message = "Indexed $indexed image files.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                update {
                    copy(
                        isIndexing = false,
                        error = error.message ?: "Failed to index gallery.",
                    )
                }
            }
        }
    }

    fun reloadList() {
        scope.launch {
            loadCachedList(keepIndexing = _uiState.value.isIndexing)
        }
    }

    fun updateQuery(value: String) {
        update { copy(query = value) }
        reloadList()
    }

    fun selectKeyword(value: String) {
        val normalized = value.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return
        update {
            copy(
                selectedKeywords = if (normalized in selectedKeywords) {
                    selectedKeywords - normalized
                } else {
                    selectedKeywords + normalized
                },
            )
        }
        reloadList()
    }

    fun clearKeywordFilter() {
        update { copy(selectedKeywords = emptyList()) }
        reloadList()
    }

    fun selectImage(id: Long) = update { copy(selectedImageId = id, keywordDraft = "") }

    fun updateKeywordDraft(value: String) = update { copy(keywordDraft = value) }

    fun addKeywordToSelected() {
        val image = _uiState.value.selectedImage ?: return
        val keyword = _uiState.value.keywordDraft
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.addKeyword(image.id, keyword) }
            }.onSuccess {
                update { copy(keywordDraft = "") }
                reloadList()
            }.onFailure { error ->
                update { copy(error = error.message ?: "Failed to add tag.") }
            }
        }
    }

    fun removeKeyword(imageId: Long, keyword: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.removeKeyword(imageId, keyword) }
            }.onSuccess {
                reloadList()
            }.onFailure { error ->
                update { copy(error = error.message ?: "Failed to remove tag.") }
            }
        }
    }

    fun deleteKeyword(keyword: String) {
        val normalized = keyword.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.deleteKeyword(normalized) }
            }.onSuccess {
                update {
                    copy(
                        selectedKeywords = selectedKeywords.filterNot { it == normalized },
                        message = "Deleted tag '$normalized'.",
                        error = null,
                    )
                }
                reloadList()
            }.onFailure { error ->
                update { copy(error = error.message ?: "Failed to delete tag.") }
            }
        }
    }

    fun cleanupUnusedKeywords() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.cleanupUnusedKeywords() }
            }.onSuccess { deleted ->
                update {
                    val activeKeywordNames = keywordStats
                        .filter { it.count > 0 }
                        .map { it.name }
                        .toSet()
                    copy(
                        selectedKeywords = selectedKeywords.filter { it in activeKeywordNames },
                        message = "Removed $deleted unused tag(s).",
                        error = null,
                    )
                }
                reloadList()
            }.onFailure { error ->
                update { copy(error = error.message ?: "Failed to clean up tags.") }
            }
        }
    }

    fun deleteSelectedImage() {
        val image = _uiState.value.selectedImage ?: return
        scope.launch {
            update { copy(isDeletingImage = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) { repository.deleteImage(image) }
            }.onSuccess {
                loadCachedList()
                update {
                    copy(
                        isDeletingImage = false,
                        message = "Deleted ${image.displayName} and its related files.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                update {
                    copy(
                        isDeletingImage = false,
                        error = error.message ?: "Failed to delete image.",
                    )
                }
            }
        }
    }

    fun tagSelectedImage() {
        val image = _uiState.value.selectedImage ?: return
        scope.launch {
            val settings = settingsStore.load()
            val roles = llmPresetStore.loadRoles()
            val preset = llmPresetStore.load().firstOrNull { it.id == roles.taggingPresetId }
            if (preset == null) {
                update { copy(error = "Select a tagging LLM preset first.") }
                return@launch
            }

            update { copy(isTaggingSelectedImage = true, message = "Tagging ${image.displayName}...", error = null) }
            imageTaggingService.tagImage(settings, preset, image)
                .onSuccess { result ->
                    loadCachedList(keepIndexing = _uiState.value.isIndexing)
                    update {
                        copy(
                            isTaggingSelectedImage = false,
                            selectedImageId = result.imageId,
                            message = if (result.tags.isEmpty()) {
                                "No new tags found for ${result.imageName}."
                            } else {
                                "Added ${result.tags.size} tag(s): ${result.tags.joinToString(", ")}"
                            },
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    update {
                        copy(
                            isTaggingSelectedImage = false,
                            error = error.message ?: "Failed to tag selected image.",
                        )
                    }
                }
        }
    }

    fun reusableParams(image: GalleryImage): GalleryReusableParams = repository.reusableParams(image)

    private suspend fun loadCachedList(keepIndexing: Boolean = false) {
        runCatching {
            withContext(Dispatchers.IO) {
                repository.listImages(_uiState.value.query, _uiState.value.selectedKeywords) to repository.listKeywordStats()
            }
        }.onSuccess { (images, keywordStats) ->
            update {
                copy(
                    images = images,
                    keywords = keywordStats.filter { it.count > 0 }.map { it.name },
                    keywordStats = keywordStats,
                    selectedImageId = selectedImageId?.takeIf { id -> images.any { it.id == id } } ?: images.firstOrNull()?.id,
                    isIndexing = keepIndexing,
                    message = if (keepIndexing) "Refreshing gallery..." else message,
                )
            }
        }.onFailure { error ->
            update { copy(isIndexing = keepIndexing, error = error.message ?: "Failed to load gallery.") }
        }
    }

    private fun update(transform: GalleryUiState.() -> GalleryUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
