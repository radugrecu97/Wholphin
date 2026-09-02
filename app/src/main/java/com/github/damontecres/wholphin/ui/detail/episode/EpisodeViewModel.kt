package com.github.damontecres.wholphin.ui.detail.episode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.DetailItemFields
import com.github.damontecres.wholphin.ui.FastDetailItemFields
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

@HiltViewModel(assistedFactory = EpisodeViewModel.Factory::class)
class EpisodeViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val itemPlaybackRepository: ItemPlaybackRepository,
        val streamChoiceService: StreamChoiceService,
        val mediaReportService: MediaReportService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val themeSongPlayer: ThemeSongPlayer,
        val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val mediaManagementService: MediaManagementService,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): EpisodeViewModel
        }

        private val _state = MutableStateFlow(EpisodeState())
        val state: StateFlow<EpisodeState> = _state

        val canDelete = MutableStateFlow(false)

        init {
            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(
                    state.map { (it.episode as? DataLoadingState.Success<BaseItem?>)?.data },
                ) { canDelete ->
                    this@EpisodeViewModel.canDelete.update { canDelete }
                }
            }
        }

        private fun fetchAndSetItem() {
            viewModelScope.launchIO {
                try {
                    val items =
                        api.itemsApi.getItems(
                            GetItemsRequest(
                                ids = listOf(itemId),
                                fields = FastDetailItemFields,
                            ),
                        ).content.items.orEmpty()
                    val dto = items.firstOrNull() ?: api.userLibraryApi.getItem(itemId).content
                    val item = BaseItem(dto)
                    _state.update { it.copy(episode = DataLoadingState.Success(item)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    showToast(context, "Error updating episode")
                }
            }
        }

        fun loadVersions(): Job =
            viewModelScope.launchIO {
                _state.update { it.copy(loadingVersions = true) }
                try {
                    val fullItem = api.userLibraryApi.getItem(itemId).content
                    val fullEp = BaseItem(fullItem)
                    val sources = fullItem.mediaSources.orEmpty()
                    val chosenStreams =
                        itemPlaybackRepository.getSelectedTracks(
                            itemId,
                            fullEp,
                            userPreferencesService.getCurrent(),
                        )
                    _state.update {
                        it.copy(
                            episode = DataLoadingState.Success(fullEp),
                            versions = sources,
                            loadingVersions = false,
                            chosenStreams = chosenStreams,
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load versions for episode %s", itemId)
                    _state.update { it.copy(loadingVersions = false) }
                }
            }

        fun init(): Job =
            viewModelScope.launchIO {
                try {
                    val prefs = userPreferencesService.getCurrent()
                    val items =
                        api.itemsApi.getItems(
                            GetItemsRequest(
                                ids = listOf(itemId),
                                fields = FastDetailItemFields,
                            ),
                        ).content.items.orEmpty()
                    val dto = items.firstOrNull() ?: api.userLibraryApi.getItem(itemId).content
                    val item = BaseItem(dto)
                    val chosenStreams =
                        itemPlaybackRepository.getSelectedTracks(item.id, item, prefs)
                    _state.update {
                        it.copy(
                            episode = DataLoadingState.Success(item),
                            chosenStreams = chosenStreams,
                            versions = null,
                        )
                    }
                    backdropService.submit(item)
                    loadVersions()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    _state.update { it.copy(episode = DataLoadingState.Error(ex)) }
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            fetchAndSetItem()
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            fetchAndSetItem()
        }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = itemPlayback,
                        trackIndex = trackIndex,
                        type = type,
                    )
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun maybePlayThemeSong(seriesId: UUID) {
            viewModelScope.launchIO {
                themeSongPlayer.playThemeFor(seriesId)
            }
        }

        fun release() {
            themeSongPlayer.stop()
        }

        fun navigateTo(destination: Destination) {
            release()
            navigationManager.navigateTo(destination)
        }

        fun clearChosenStreams(chosenStreams: ChosenStreams?) {
            viewModelScope.launchIO {
                itemPlaybackRepository.deleteChosenStreams(chosenStreams)
                state.value.episode.let { item ->
                    if (item is DataLoadingState.Success<BaseItem>) {
                        val result =
                            itemPlaybackRepository.getSelectedTracks(
                                itemId,
                                item.data,
                                userPreferencesService.getCurrent(),
                            )
                        _state.update { it.copy(chosenStreams = result) }
                    }
                }
            }
        }

        fun playVersion(source: MediaSourceInfo) {
            val ep = (state.value.episode as? DataLoadingState.Success<BaseItem>)?.data ?: return
            val sourceId = source.id?.toUUIDOrNull()
            if (sourceId != null) {
                savePlayVersion(ep, sourceId)
            }
            val resumePosition = ep.data.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO
            navigateTo(
                Destination.Playback(
                    itemId = ep.id,
                    positionMs = resumePosition.inWholeMilliseconds,
                    sourceId = source.id,
                ),
            )
        }

        fun deleteItem(item: BaseItem) {
            deleteItem(context, mediaManagementService, item) {
                navigationManager.goBack()
            }
        }
    }

data class EpisodeState(
    val episode: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val chosenStreams: ChosenStreams? = null,
    val versions: List<MediaSourceInfo>? = null,
    val loadingVersions: Boolean = false,
)
