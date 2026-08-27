package com.lagradost.cloudstream3

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.plugins.AndroidPluginLoader
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.player.native.AndroidVideoPlayer
import com.lagradost.cloudstream3.shared.ui.CloudstreamApp
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.Globals
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private var activityResultLauncher: ActivityResultLauncher<Intent>? = null

    companion object {
        const val TAG = "MAINACT"
        const val API_NAME_EXTRA_KEY = "API_NAME_EXTRA_KEY"

        var lastError: String? = null
        var nextSearchQuery: String? = null

        private val filesToDelete = ConcurrentHashMap.newKeySet<String>()

        val afterPluginsLoadedEvent = Event<Boolean>()
        val mainPluginsLoadedEvent = Event<Boolean>()
        val afterRepositoryLoadedEvent = Event<Boolean>()
        val bookmarksUpdatedEvent = Event<Boolean>()
        val reloadHomeEvent = Event<Boolean>()
        val reloadLibraryEvent = Event<Boolean>()
        val reloadAccountEvent = Event<Boolean>()

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

        fun deleteFileOnExit(file: File) {
            filesToDelete.add(file.path)
        }

        fun launchResult(intent: Intent, options: ActivityOptionsCompat? = null) {
            (CommonActivity.activity as? MainActivity)?.launchResult(intent, options)
        }
    }

    fun launchResult(intent: Intent, options: ActivityOptionsCompat? = null) {
        activityResultLauncher?.launch(intent, options)
    }

    private fun registerResultLauncher() {
        activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                handleActivityResult(result.data)
            }
        }
    }

    private fun handleActivityResult(data: Intent?) {
        val actionUid = AppPreferenceManager.getStringSync("last_click_action") ?: return
        val action = VideoClickActionHolder.getByUniqueId(actionUid) as? OpenInAppAction ?: return
        action.onResultSafe(this, data)
        AppPreferenceManager.deletePreferenceSync("last_click_action")
        AppPreferenceManager.deletePreferenceSync("last_opened")
    }

    private fun updatePlayerOrientation(isActive: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isTv = Globals.isLayout(Globals.TV)

        if (isActive) {
            if (!isTv) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            if (!isTv) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            }
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerResultLauncher()
        CommonActivity.init(this)

        setContent {
            val database = remember { DatabaseDriverFactory(this).createDatabase() }
            val pluginLoader = remember { AndroidPluginLoader(applicationContext) }
            val videoPlayer = remember { AndroidVideoPlayer(applicationContext) }

            var backHandler: (() -> Boolean)? by remember { mutableStateOf(null) }
            BackHandler(enabled = true) {
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
                onPlayerStateChanged = ::updatePlayerOrientation,
                videoPlayerContent = { vPlayer, modifier ->
                    val androidPlayer = vPlayer as? AndroidVideoPlayer
                    val exoPlayerFlow = remember(androidPlayer) {
                        androidPlayer?.exoPlayerState ?: MutableStateFlow(null)
                    }
                    val exoPlayer by exoPlayerFlow.collectAsState()
                    val resizeModeFlow = remember(androidPlayer) {
                        androidPlayer?.resizeMode ?: MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    }
                    val resizeMode by resizeModeFlow.collectAsState()

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                setShutterBackgroundColor(Color.BLACK)
                                setBackgroundColor(Color.BLACK)
                                keepScreenOn = true
                                player = exoPlayer ?: androidPlayer?.exoPlayer
                                this.resizeMode = resizeMode
                            }
                        },
                        update = { playerView ->
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
        activityResultLauncher = null
        filesToDelete.forEach { path ->
            File(path).deleteRecursively()
        }
        filesToDelete.clear()
        super.onDestroy()
    }
}
