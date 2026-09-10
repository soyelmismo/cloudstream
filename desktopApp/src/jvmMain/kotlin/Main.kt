import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.api.Log
import com.lagradost.cloudstream3.desktop.player.DesktopVideoPlayer
import com.lagradost.cloudstream3.desktop.player.DesktopVideoSurface
import com.lagradost.cloudstream3.desktop.plugins.JvmPluginLoader
import com.lagradost.cloudstream3.desktop.window.DesktopEnvironment
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.ui.CloudstreamApp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.cloud_2_gradient
import org.jetbrains.compose.resources.painterResource
import java.io.File

private const val TAG = "CloudStreamMain"

@OptIn(com.lagradost.cloudstream3.UnsafeSSL::class)
fun main() {
    DesktopEnvironment.configureGraphicsPipelines()

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

        val userHome = System.getProperty("user.home") ?: "."
        val appCacheDir = File(userHome, ".cloudstream/cache").apply { if (!exists()) mkdirs() }
        val sharedCookieJar = com.lagradost.cloudstream3.network.SessionCookieJar()
        val sharedClient = com.lagradost.cloudstream3.network.buildSharedOkHttpClient(
            cacheDir = appCacheDir,
            dnsPreference = 0,
            ignoreSSL = false,
            cookieJar = sharedCookieJar
        )
        com.lagradost.cloudstream3.app.baseClient = sharedClient
        com.lagradost.cloudstream3.app.defaultHeaders = mapOf("User-Agent" to com.lagradost.cloudstream3.USER_AGENT)
        com.lagradost.cloudstream3.insecureApp.baseClient = com.lagradost.cloudstream3.network.buildSharedOkHttpClient(
            cacheDir = appCacheDir,
            dnsPreference = 0,
            ignoreSSL = true,
            cookieJar = sharedCookieJar
        )
        com.lagradost.cloudstream3.insecureApp.defaultHeaders = mapOf("User-Agent" to com.lagradost.cloudstream3.USER_AGENT)

        org.schabi.newpipe.extractor.NewPipe.init(com.lagradost.cloudstream3.desktop.JvmDownloader.getInstance())

        val database = remember {
            DatabaseDriverFactory().createDatabase()
        }

        val pluginLoader = remember {
            val loader = JvmPluginLoader()
            val pluginsDir = File(userHome, ".cloudstream/plugins")
            if (!pluginsDir.exists()) {
                pluginsDir.mkdirs()
            }

            pluginsDir.listFiles { file -> file.extension.lowercase() in listOf("jar", "cs3") }?.forEach { pluginFile ->
                try {
                    val plugin = loader.loadPlugin(pluginFile.absolutePath)
                    if (plugin != null) {
                        Log.i(TAG, "Loaded desktop plugin: ${plugin.manifest?.name ?: pluginFile.nameWithoutExtension}")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to load plugin at ${pluginFile.absolutePath}: ${e.message}")
                }
            }
            loader
        }

        val player = remember { DesktopVideoPlayer() }

        DisposableEffect(player, database) {
            onDispose {
                DesktopEnvironment.releaseResources(player, database)
            }
        }

        Window(
            onCloseRequest = {
                DesktopEnvironment.releaseResources(player, database)
                exitApplication()
                kotlin.system.exitProcess(0)
            },
            state = windowState,
            title = "CloudStream",
            icon = painterResource(Res.drawable.cloud_2_gradient)
        ) {
            val toggleFullscreen: () -> Unit = {
                windowState.placement = if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen) {
                    androidx.compose.ui.window.WindowPlacement.Floating
                } else {
                    androidx.compose.ui.window.WindowPlacement.Fullscreen
                }
            }

            CloudstreamApp(
                database = database,
                player = player,
                pluginLoader = pluginLoader,
                onToggleFullscreen = toggleFullscreen,
                videoPlayerContent = { vPlayer, modifier ->
                    DesktopVideoSurface(
                        player = vPlayer as DesktopVideoPlayer,
                        modifier = modifier
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
