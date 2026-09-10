package com.lagradost.cloudstream3

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.NO_ID
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isNotEmpty
import com.lagradost.cloudstream4.generated.resources.*
import com.google.android.gms.cast.framework.CastSession
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigationrail.NavigationRailView
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.VotingApi
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.player.native.PlayerPipHelper.isPIPPossible
import com.lagradost.cloudstream3.shared.player.native.Torrent
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.shared.syncproviders.AuthRepo
import com.lagradost.cloudstream3.shared.syncproviders.SyncConfig
import com.lagradost.cloudstream3.utils.AppContextUtils
import com.lagradost.cloudstream3.utils.AppContextUtils.isRtl
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.Globals.TV
import com.lagradost.cloudstream3.utils.Globals.isLayout
import com.lagradost.cloudstream3.utils.Globals.updateTv
import com.lagradost.cloudstream3.utils.UIHelper.showInputMethod
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.asString
import com.lagradost.cloudstream3.utils.asStringNull
import com.lagradost.cloudstream3.utils.txt
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import org.schabi.newpipe.extractor.NewPipe

enum class FocusDirection {
    Start,
    End,
    Up,
    Down,
}

object CommonActivity {

    private var _activity: WeakReference<Activity>? = null
    var activity
        get() = _activity?.get()
        private set(value) {
            _activity = WeakReference(value)
        }

    @MainThread
    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
    }

    @MainThread
    fun Activity?.getCastSession(): CastSession? {
        return try {
            this?.let { com.google.android.gms.cast.framework.CastContext.getSharedInstance(it).sessionManager.currentCastSession }
        } catch (_: Throwable) {
            null
        }
    }

    val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

    // screenWidth and screenHeight does always
    // refer to the screen while in landscape mode
    val screenWidth: Int
        get() {
            return max(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    val screenHeight: Int
        get() {
            return min(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    val screenWidthWithOrientation: Int
        get() {
            return displayMetrics.widthPixels
        }
    val screenHeightWithOrientation: Int
        get() {
            return displayMetrics.heightPixels
        }

    var isPipDesired: Boolean = false
    var isInPIPMode: Boolean = false

    val onColorSelectedEvent = Event<Pair<Int, Int>>()
    val onDialogDismissedEvent = Event<Int>()

    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null
    var appliedTheme: Int = 0
    var appliedColor: Int = 0

    private var currentToast: Toast? = null

    fun showToast(@StringRes message: Int, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, act.getString(message), duration)
        }
    }

    fun showToast(resource: org.jetbrains.compose.resources.StringResource, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, txt(resource), duration ?: Toast.LENGTH_SHORT)
        }
    }

    fun showToast(message: String?, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, message, duration)
        }
    }

    fun showToast(message: UiText?, duration: Int? = null) {
        val act = activity ?: return
        if (message == null) return
        act.runOnUiThread {
            showToast(act, message.asString(act), duration)
        }
    }

    @MainThread
    fun showToast(act: Activity?, text: UiText, duration: Int) {
        if (act == null) return
        text.asStringNull(act)?.let {
            showToast(act, it, duration)
        }
    }

    /** duration is Toast.LENGTH_SHORT if null*/
    @MainThread
    fun showToast(act: Activity?, @StringRes message: Int, duration: Int? = null) {
        if (act == null) return
        showToast(act, act.getString(message), duration)
    }

    /** duration is Toast.LENGTH_SHORT if null*/
    @MainThread
    fun showToast(act: Activity?, resource: org.jetbrains.compose.resources.StringResource, duration: Int? = null) {
        if (act == null) return
        showToast(act, txt(resource), duration ?: Toast.LENGTH_SHORT)
    }

    const val TAG = "COMPACT"

    /** duration is Toast.LENGTH_SHORT if null*/
    @MainThread
    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (act == null || message == null) {
            Log.w(TAG, "invalid showToast act = $act message = $message")
            return
        }
        Log.i(TAG, "showToast = $message")

        try {
            currentToast?.cancel()
        } catch (e: Exception) {
            logError(e)
        }

        try {
            val toast = Toast.makeText(act, message.trim(), duration ?: Toast.LENGTH_SHORT)
            currentToast = toast
            toast.show()
        } catch (e: Exception) {
            logError(e)
        }
    }

    /**
     * Set locale
     * @param languageTag shall a IETF BCP 47 conformant tag.
     * Check [com.lagradost.cloudstream3.utils.SubtitleHelper].
     *
     * See locales on:
     * https://github.com/unicode-org/cldr-json/blob/main/cldr-json/cldr-core/availableLocales.json
     * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
     * https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r2/core/res/res/values/locale_config.xml
     * https://iso639-3.sil.org/code_tables/639/data/all
     */
    fun setLocale(context: Context?, languageTag: String?) {
        if (context == null || languageTag == null) return
        val locale = Locale.forLanguageTag(languageTag.replace('_', '-'))
        Locale.setDefault(locale)
    }

    fun Context.updateLocale() {
        val localeCode = AppPreferenceManager.getStringSync(AppPreferenceManager.KEY_APP_LOCALE, null)
        setLocale(this, localeCode)
    }

    fun init(act: Activity) {
        setActivityInstance(act)
        ioSafe { Torrent.deleteAllFiles() }
        val componentActivity = activity as? ComponentActivity ?: return

        componentActivity.updateLocale()
        componentActivity.updateTv()
        SyncConfig.init(
            anilistKey = BuildConfig.ANILIST_KEY,
            malKey = BuildConfig.MAL_KEY,
            simklClientId = BuildConfig.SIMKL_CLIENT_ID,
            simklClientSecret = BuildConfig.SIMKL_CLIENT_SECRET
        )
        AuthRepo.openBrowserHandler = { url -> AppContextUtils.openBrowser(url) }
        AuthRepo.showToastHandler = { text -> showToast(text) }
        VotingApi.showToastHandler = { text -> showToast(text) }
        VotingApi.canVoteHandler = { pluginUrl -> PluginManager.urlPlugins.contains(pluginUrl) }
        AccountManager.initMainAPI()
        NewPipe.init(DownloaderTestImpl.getInstance())

        requestNotificationPermissionIfRequired(componentActivity)
    }

    private fun requestNotificationPermissionIfRequired(componentActivity: ComponentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                componentActivity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val requestPermissionLauncher = componentActivity.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                Log.d(TAG, "Notification permission: $isGranted")
            }
            requestPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun Activity.enterPIPMode() {
        if (!isPipDesired || !this.isPIPPossible()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    enterPictureInPictureMode()
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @Suppress("DEPRECATION")
                enterPictureInPictureMode()
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun onUserLeaveHint(act: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        act.enterPIPMode()
    }

    fun updateTheme(act: Activity) {
        if (AppPreferenceManager.getStringSync(AppPreferenceManager.KEY_APP_THEME, "AmoledLight") == "System"
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            loadThemes(act)
        }
    }

    private fun mapSystemTheme(act: Activity): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val currentNightMode =
                act.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return when (currentNightMode) {
                Configuration.UI_MODE_NIGHT_NO -> R.style.LightMode
                else -> R.style.AppTheme
            }
        } else {
            return R.style.AppTheme
        }
    }

    private fun resolveThemeRes(themeName: String?, act: Activity): Int {
        return when (themeName) {
            "System" -> mapSystemTheme(act)
            "Black" -> R.style.AppTheme
            "Light" -> R.style.LightMode
            "Amoled" -> R.style.AmoledMode
            "AmoledLight" -> R.style.AmoledModeLight
            "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.style.MonetMode else R.style.AppTheme
            "Dracula" -> R.style.DraculaMode
            "Lavender" -> R.style.LavenderMode
            "SilentBlue" -> R.style.SilentBlueMode
            else -> R.style.AppTheme
        }
    }

    private val overlayThemes = mapOf(
        "Normal" to R.style.OverlayPrimaryColorNormal,
        "DandelionYellow" to R.style.OverlayPrimaryColorDandelionYellow,
        "CarnationPink" to R.style.OverlayPrimaryColorCarnationPink,
        "Orange" to R.style.OverlayPrimaryColorOrange,
        "DarkGreen" to R.style.OverlayPrimaryColorDarkGreen,
        "Maroon" to R.style.OverlayPrimaryColorMaroon,
        "NavyBlue" to R.style.OverlayPrimaryColorNavyBlue,
        "Grey" to R.style.OverlayPrimaryColorGrey,
        "White" to R.style.OverlayPrimaryColorWhite,
        "CoolBlue" to R.style.OverlayPrimaryColorCoolBlue,
        "Brown" to R.style.OverlayPrimaryColorBrown,
        "Purple" to R.style.OverlayPrimaryColorPurple,
        "Green" to R.style.OverlayPrimaryColorGreen,
        "GreenApple" to R.style.OverlayPrimaryColorGreenApple,
        "Red" to R.style.OverlayPrimaryColorRed,
        "Banana" to R.style.OverlayPrimaryColorBanana,
        "Party" to R.style.OverlayPrimaryColorParty,
        "Pink" to R.style.OverlayPrimaryColorPink,
        "Lavender" to R.style.OverlayPrimaryColorLavender,
    )

    private fun resolveOverlayThemeRes(colorName: String?): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (colorName == "Monet") return R.style.OverlayPrimaryColorMonet
            if (colorName == "Monet2") return R.style.OverlayPrimaryColorMonetTwo
        }
        return overlayThemes[colorName] ?: R.style.OverlayPrimaryColorNormal
    }

    fun loadThemes(act: Activity?) {
        if (act == null) return
        val themeKey = AppPreferenceManager.getStringSync(AppPreferenceManager.KEY_APP_THEME, "AmoledLight")
        val colorKey = AppPreferenceManager.getStringSync("primary_color_key", "Normal")

        val currentTheme = resolveThemeRes(themeKey, act)
        val currentOverlayTheme = resolveOverlayThemeRes(colorKey)

        act.theme.applyStyle(currentTheme, true)
        act.theme.applyStyle(currentOverlayTheme, true)
        appliedTheme = currentTheme
        appliedColor = currentOverlayTheme
        act.updateTv()
        if (isLayout(TV)) act.theme.applyStyle(R.style.AppThemeTvOverlay, true)
        act.theme.applyStyle(R.style.LoadedStyle, true)
    }

    private fun localLook(from: View, id: Int): View? {
        if (id == NO_ID) return null
        var currentLook: View = from
        for (i in 0..15) {
            currentLook.findViewById<View?>(id)?.let { return it }
            currentLook = (currentLook.parent as? View) ?: break
        }
        return null
    }

    private fun View.hasContent(): Boolean {
        return isShown && when (this) {
            is ViewGroup -> this.isNotEmpty()
            else -> true
        }
    }

    private fun findRootViewById(root: Any?, id: Int): View? {
        return when (root) {
            is Activity -> root.findViewById(id)
            is View -> root.rootView.findViewById(id)
            else -> null
        }
    }

    private fun View.canAcceptFocus(hasContent: Boolean): Boolean {
        if (isFocusable) return true
        if (!hasContent) return true
        val viewGroup = this as? ViewGroup ?: return false
        return viewGroup.descendantFocusability == ViewGroup.FOCUS_AFTER_DESCENDANTS && viewGroup.isNotEmpty()
    }

    private fun resolveCompositeChildFocus(view: View): View? {
        return when (view) {
            is ChipGroup -> view.children.firstOrNull { it.isFocusable && it.isShown }
            is NavigationRailView -> view.findViewById(view.selectedItemId)
            else -> null
        }
    }

    fun continueGetNextFocus(
        root: Any?,
        view: View,
        direction: FocusDirection,
        nextId: Int,
        depth: Int = 0
    ): View? {
        if (nextId == NO_ID) return null

        val candidate = findRootViewById(root, nextId) ?: return null
        val targetView = localLook(view, nextId) ?: candidate
        val hasContent = targetView.hasContent()

        if (!targetView.canAcceptFocus(hasContent)) return null

        if (!hasContent) {
            if (targetView == view) return null
            return getNextFocus(root, targetView, direction, depth + 1)
        }

        return resolveCompositeChildFocus(targetView) ?: targetView
    }

    private fun View.getHorizontalFocusId(isStart: Boolean): Int {
        val shouldGoRight = if (isRtl()) isStart else !isStart
        return if (shouldGoRight) nextFocusRightId else nextFocusLeftId
    }

    private fun View.getDirectionalNextFocusId(direction: FocusDirection): Int {
        val id = when (direction) {
            FocusDirection.Start -> getHorizontalFocusId(isStart = true)
            FocusDirection.End -> getHorizontalFocusId(isStart = false)
            FocusDirection.Up -> nextFocusUpId
            FocusDirection.Down -> nextFocusDownId
        }
        return if (id != NO_ID) id else nextFocusForwardId
    }

    fun getNextFocus(
        root: Any?,
        view: View?,
        direction: FocusDirection,
        depth: Int = 0
    ): View? {
        if (view == null || root == null || depth >= 10) return null

        val nextId = view.getDirectionalNextFocusId(direction)
        if (nextId == NO_ID) return null

        return continueGetNextFocus(root, view, direction, nextId, depth)
    }

    fun onKeyDown(act: Activity?, keyCode: Int, event: KeyEvent?): Boolean? {
        return null
    }

    private fun keyCodeToFocusDirection(keyCode: Int): FocusDirection? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Start
            KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.End
            KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
            else -> null
        }
    }

    private fun handleDpadNavigation(act: Activity, currentFocus: View, event: KeyEvent): Boolean {
        val direction = keyCodeToFocusDirection(event.keyCode) ?: return false
        val nextView = getNextFocus(act, currentFocus, direction) ?: return false
        nextView.requestFocus()
        keyEventListener?.invoke(Pair(event, true))
        return true
    }

    @SuppressLint("RestrictedApi")
    private fun handleSearchInputTrigger(currentFocus: View?, keyCode: Int) {
        val isConfirmKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        if (!isConfirmKey) return
        val isSearchField = currentFocus is SearchView || currentFocus is SearchView.SearchAutoComplete
        if (isSearchField) {
            showInputMethod(currentFocus.findFocus())
        }
    }

    private fun tryHandleKeyDown(act: Activity, currentFocus: View, event: KeyEvent): Boolean {
        if (handleDpadNavigation(act, currentFocus, event)) return true
        handleSearchInputTrigger(currentFocus, event.keyCode)
        return false
    }

    fun dispatchKeyEvent(act: Activity?, event: KeyEvent?): Boolean? {
        if (act == null || event == null) return null

        val currentFocus = act.currentFocus
        if (event.action == KeyEvent.ACTION_DOWN && currentFocus != null) {
            if (tryHandleKeyDown(act, currentFocus, event)) return true
        }

        return if (keyEventListener?.invoke(Pair(event, false)) == true) true else null
    }
}
