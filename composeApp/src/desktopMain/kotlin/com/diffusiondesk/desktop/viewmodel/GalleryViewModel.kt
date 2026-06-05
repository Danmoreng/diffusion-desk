package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.GalleryImage
import com.diffusiondesk.desktop.core.GalleryRepository
import com.diffusiondesk.desktop.core.GalleryReusableParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val keywords: List<String> = emptyList(),
    val selectedImageId: Long? = null,
    val query: String = "",
    val selectedKeyword: String = "",
    val keywordDraft: String = "",
    val isIndexing: Boolean = false,
    val message: String = "",
    val error: String? = null,
) {
    val selectedImage: GalleryImage? get() = images.firstOrNull { it.id == selectedImageId } ?: images.firstOrNull()
}

class GalleryViewModel(
    private val scope: CoroutineScope,
    private val repository: GalleryRepository,
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
                    val images = repository.listImages(_uiState.value.query, _uiState.value.selectedKeyword)
                    val keywords = repository.listKeywords()
                    Triple(indexed, images, keywords)
                }
            }.onSuccess { (indexed, images, keywords) ->
                update {
                    copy(
                        images = images,
                        keywords = keywords,
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
        update { copy(selectedKeyword = if (selectedKeyword == value) "" else value) }
        reloadList()
    }

    fun clearKeywordFilter() {
        update { copy(selectedKeyword = "") }
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
                update { copy(error = error.message ?: "Failed to add keyword.") }
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
                update { copy(error = error.message ?: "Failed to remove keyword.") }
            }
        }
    }

    fun reusableParams(image: GalleryImage): GalleryReusableParams = repository.reusableParams(image)

    private suspend fun loadCachedList(keepIndexing: Boolean = false) {
        runCatching {
            withContext(Dispatchers.IO) {
                repository.listImages(_uiState.value.query, _uiState.value.selectedKeyword) to repository.listKeywords()
            }
        }.onSuccess { (images, keywords) ->
            update {
                copy(
                    images = images,
                    keywords = keywords,
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
