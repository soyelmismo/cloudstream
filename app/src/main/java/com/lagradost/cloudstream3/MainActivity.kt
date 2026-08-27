package com.lagradost.cloudstream3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.lagradost.cloudstream3.plugins.AndroidPluginLoader
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.ui.CloudstreamApp
import com.lagradost.cloudstream3.shared.player.native.AndroidVideoPlayer
import com.lagradost.cloudstream3.utils.Event
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        var activityResultLauncher: ActivityResultLauncher<Intent>? = null

        const val TAG = "MAINACT"
        var lastError: String? = null

        fun setLastError(context: Context) {
            if (lastError != null) return

            val errorFile = context.filesDir.resolve("last_error")
            if (errorFile.exists() && errorFile.isFile) {
                lastError = errorFile.readText(Charset.defaultCharset())
                errorFile.delete()
            } else {
                lastError = null
            }
        }

        const val API_NAME_EXTRA_KEY = "API_NAME_EXTRA_KEY"
        private val filesToDelete = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun deleteFileOnExit(file: File) {
            filesToDelete.add(file.path)
        }

        var nextSearchQuery: String? = null

        val afterPluginsLoadedEvent = Event<Boolean>()
        val mainPluginsLoadedEvent = Event<Boolean>()
        val afterRepositoryLoadedEvent = Event<Boolean>()
        val bookmarksUpdatedEvent = Event<Boolean>()
        val reloadHomeEvent = Event<Boolean>()
        val reloadLibraryEvent = Event<Boolean>()
        val reloadAccountEvent = Event<Boolean>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommonActivity.init(this)

        setContent {
            val database = remember { DatabaseDriverFactory(this).createDatabase() }
            val pluginLoader = remember { AndroidPluginLoader(applicationContext) }
            val videoPlayer = remember { AndroidVideoPlayer(applicationContext) }

            var backHandler: (() -> Boolean)? by remember { androidx.compose.runtime.mutableStateOf(null) }
            androidx.activity.compose.BackHandler(enabled = true) {
                if (backHandler?.invoke() != true) {
                    finish()
                }
            }

            DisposableEffect(videoPlayer) {
                onDispose {
                    videoPlayer.release()
                }
            }

            CloudstreamApp(
                database = database,
                player = videoPlayer,
                pluginLoader = pluginLoader,
                onRegisterBackHandler = { handler ->
                    backHandler = handler
                },
                onPlayerStateChanged = { isActive ->
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    if (isActive) {
                        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                },
                videoPlayerContent = { vPlayer, modifier ->
                    val androidPlayer = vPlayer as? AndroidVideoPlayer
                    val exoPlayerFlow = remember(androidPlayer) {
                        androidPlayer?.exoPlayerState ?: MutableStateFlow(null)
                    }
                    val exoPlayer by exoPlayerFlow.collectAsState()
                    val resizeModeFlow = remember(androidPlayer) {
                        androidPlayer?.resizeMode ?: MutableStateFlow(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    }
                    val resizeMode by resizeModeFlow.collectAsState()
                    Log.d("CloudStreamDebug", "MainActivity videoPlayerContent composed. exoPlayer=$exoPlayer androidPlayer=$androidPlayer")

                    AndroidView(
                        factory = { ctx ->
                            Log.d("CloudStreamDebug", "MainActivity PlayerView factory: player=$exoPlayer")
                            PlayerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                setBackgroundColor(android.graphics.Color.BLACK)
                                keepScreenOn = true
                                player = exoPlayer ?: androidPlayer?.exoPlayer
                                this.resizeMode = resizeMode
                            }
                        },
                        update = { playerView ->
                            Log.d("CloudStreamDebug", "MainActivity PlayerView update: player=$exoPlayer")
                            val currentExo = exoPlayer ?: androidPlayer?.exoPlayer
                            if (playerView.player != currentExo) {
                                playerView.player = currentExo
                            }
                            if (playerView.resizeMode != resizeMode) {
                                playerView.resizeMode = resizeMode
                            }
                        },
                        modifier = modifier
                    )
                }
            )
        }
    }

    override fun onDestroy() {
        filesToDelete.forEach { path ->
            val result = File(path).deleteRecursively()
            if (result) {
                Log.d(TAG, "Deleted temporary file: $path")
            } else {
                Log.d(TAG, "Failed to delete temporary file: $path")
            }
        }
        filesToDelete.clear()
        super.onDestroy()
    }
}
