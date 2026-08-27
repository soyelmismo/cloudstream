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
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.ui.CloudstreamApp
import cloudstream.shared_ui.generated.resources.Res
import cloudstream.shared_ui.generated.resources.cloud_2_gradient
import org.jetbrains.compose.resources.painterResource
import java.io.File

private const val TAG = "CloudStreamMain"

@OptIn(com.lagradost.cloudstream3.UnsafeSSL::class)
fun main() {
    // -------------------------------------------------------------------------
    // Skiko & Hardware Acceleration Optimization
    // Direct native GPU swapchain presentation without software blending throttle
    // -------------------------------------------------------------------------
    val osName = System.getProperty("os.name")?.lowercase() ?: ""
    if (System.getProperty("skiko.renderApi") == null && System.getenv("SKIKO_RENDER_API") == null) {
        val optimalRenderApi = when {
            osName.contains("mac") || osName.contains("darwin") -> "METAL"
            osName.contains("win") -> "DIRECTX"
            else -> "OPENGL"
        }
        System.setProperty("skiko.renderApi", optimalRenderApi)
    }

    // Enable VSync synchronization for smooth 60/120+ FPS window rendering
    if (System.getProperty("skiko.vsync.enabled") == null) {
        System.setProperty("skiko.vsync.enabled", "true")
    }

    // Disallow CPU software rasterization fallback when hardware GPU is available
    if (System.getProperty("skiko.rendering.software") == null) {
        System.setProperty("skiko.rendering.software", "false")
    }

    // Prevent AWT background erase flicker on window resize/draw
    System.setProperty("sun.awt.noerasebackground", "true")

    // Optimize underlying 2D hardware pipelines where applicable
    if (osName.contains("win")) {
        if (System.getProperty("sun.java2d.d3d") == null) {
            System.setProperty("sun.java2d.d3d", "true")
        }
    } else if (!osName.contains("mac")) {
        if (System.getProperty("sun.java2d.opengl") == null) {
            System.setProperty("sun.java2d.opengl", "true")
        }
    }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    // -------------------------------------------------------------------------
    // 0. Initialize Extractors & Network Timeout Settings
    // -------------------------------------------------------------------------
    val userHome = System.getProperty("user.home") ?: "."
    val appCacheDir = File(userHome, ".cloudstream/cache").apply { if (!exists()) mkdirs() }
    // Read dns_pref if available from preferences or default to 0
    val dnsPref = 0 // or read from properties/datastore
    val sharedCookieJar = com.lagradost.cloudstream3.network.SessionCookieJar()
    val sharedClient = com.lagradost.cloudstream3.network.buildSharedOkHttpClient(
        cacheDir = appCacheDir,
        dnsPreference = dnsPref,
        ignoreSSL = false,
        cookieJar = sharedCookieJar
    )
    com.lagradost.cloudstream3.app.baseClient = sharedClient
    com.lagradost.cloudstream3.app.defaultHeaders = mapOf("User-Agent" to com.lagradost.cloudstream3.USER_AGENT)
    com.lagradost.cloudstream3.insecureApp.baseClient = com.lagradost.cloudstream3.network.buildSharedOkHttpClient(
        cacheDir = appCacheDir,
        dnsPreference = dnsPref,
        ignoreSSL = true,
        cookieJar = sharedCookieJar
    )
    com.lagradost.cloudstream3.insecureApp.defaultHeaders = mapOf("User-Agent" to com.lagradost.cloudstream3.USER_AGENT)

    org.schabi.newpipe.extractor.NewPipe.init(com.lagradost.cloudstream3.desktop.JvmDownloader.getInstance())

    // -------------------------------------------------------------------------
    // 1. Initialize Room KMP Persistence
    // -------------------------------------------------------------------------
    val database = remember {
        DatabaseDriverFactory().createDatabase()
    }

    // -------------------------------------------------------------------------
    // 2. Initialize JVM Plugin System & Discover Plugins
    // -------------------------------------------------------------------------
    val pluginLoader = remember {
        val loader = JvmPluginLoader()
        val userHome = System.getProperty("user.home") ?: "."
        val pluginsDir = File(userHome, ".cloudstream/plugins")
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        // Load all .jar plugins in the plugins directory
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

    // -------------------------------------------------------------------------
    // 3. Initialize VLCJ Native Video Player
    // -------------------------------------------------------------------------
    val player = remember { DesktopVideoPlayer() }

    DisposableEffect(player, database) {
        onDispose {
            try {
                player.release()
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing video player: ${e.message}")
            }
            try {
                if (database !is com.lagradost.cloudstream3.shared.persistence.database.DefaultAppDatabase) {
                    database.close()
                }
            } catch (e: Throwable) {
                // Ignored
            }
        }
    }

    Window(
        onCloseRequest = {
            try {
                player.release()
            } catch (e: Throwable) {
                // Ignore
            }
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
