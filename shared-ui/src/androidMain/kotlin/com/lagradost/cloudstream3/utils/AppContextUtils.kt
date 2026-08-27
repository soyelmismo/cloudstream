package com.lagradost.cloudstream3.utils

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.tv.TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.util.Log
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import androidx.tvprovider.media.tv.WatchNextProgram.fromCursor
import androidx.viewpager2.widget.ViewPager2
import coil3.Extras
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.shared.player.native.SubtitleData
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager.Companion.APP_STRING_RESUME_WATCHING
import com.lagradost.cloudstream3.shared.syncproviders.providers.Kitsu
import cloudstream.shared_ui.generated.resources.Res
import cloudstream.shared_ui.generated.resources.season
import cloudstream.shared_ui.generated.resources.season_short
import cloudstream.shared_ui.generated.resources.episode
import cloudstream.shared_ui.generated.resources.episode_short
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

object AppContextUtils {
    fun RecyclerView.isRecyclerScrollable(): Boolean {
        val layoutManager =
            this.layoutManager as? LinearLayoutManager?
        val adapter = adapter
        return if (layoutManager == null || adapter == null) false else layoutManager.findLastCompletelyVisibleItemPosition() < adapter.itemCount - 7
    }

    fun View.isLtr() = this.layoutDirection == LAYOUT_DIRECTION_LTR
    fun View.isRtl() = this.layoutDirection == LAYOUT_DIRECTION_RTL

    fun BottomSheetDialog?.ownHide() {
        this?.hide()
    }

    fun BottomSheetDialog?.ownShow() {
        this?.window?.setWindowAnimations(-1)
        this?.show()
        Handler(Looper.getMainLooper()).postDelayed({
            this?.window?.setWindowAnimations(com.google.android.material.R.style.Animation_Design_BottomSheetDialog)
        }, 200)
    }

    fun String?.html(): Spanned {
        return getHtmlText(this ?: return "".toSpanned())
    }

    private fun getHtmlText(text: String): Spanned {
        return try {
            HtmlCompat.fromHtml(
                text, HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } catch (e: Exception) {
            logError(e)
            text.toSpanned()
        }
    }

    /** Get channel ID by name */
    @SuppressLint("RestrictedApi")
    private fun buildWatchNextProgramUri(
        context: Context,
        card: ResumeWatchingResult,
        resumeWatching: ResumeWatching?
    ): WatchNextProgram {
        val isSeries = card.type?.isMovieType() == false
        val title = if (isSeries) {
            context.getNameFull(card.name, card.episode, card.season)
        } else {
            card.name
        }

        val builder = WatchNextProgram.Builder()
            .setEpisodeTitle(title)
            .setType(
                if (isSeries) {
                    TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                } else TvContractCompat.WatchNextPrograms.TYPE_MOVIE
            )
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(title)
            .setPosterArtUri(card.posterUrl?.toUri())
            .setIntentUri((card.id?.let {
                "$APP_STRING_RESUME_WATCHING://$it"
            } ?: card.url).toUri())
            .setInternalProviderId(card.url)
            .setLastEngagementTimeUtcMillis(
                resumeWatching?.updateTime ?: System.currentTimeMillis()
            )

        card.watchPos?.let {
            builder.setDurationMillis(it.duration.toInt())
            builder.setLastPlaybackPositionMillis(it.position.toInt())
        }

        if (isSeries)
            card.episode?.let {
                builder.setEpisodeNumber(it)
            }

        return builder.build()
    }

    fun ViewPager2.reduceDragSensitivity(f: Int = 4) {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * f)
    }

    fun ContentLoadingProgressBar?.animateProgressTo(to: Int) {
        if (this == null) return
        val animation: ObjectAnimator = ObjectAnimator.ofInt(
            this,
            "progress",
            this.progress,
            to
        )
        animation.duration = 500
        animation.setAutoCancel(true)
        animation.interpolator = DecelerateInterpolator()
        animation.start()
    }

    fun Context.createNotificationChannel(
        channelId: String,
        channelName: String,
        description: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(channelId, channelName, importance).apply {
                    this.description = description
                }

            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("RestrictedApi")
    fun getAllWatchNextPrograms(context: Context): Set<Long> {
        val COLUMN_WATCH_NEXT_ID_INDEX = 0
        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ null
        )
        val set = mutableSetOf<Long>()
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    set.add(cursor.getLong(COLUMN_WATCH_NEXT_ID_INDEX))
                } while (it.moveToNext())
            }
        }
        return set
    }

    /**
     * Find the Watch Next program for given id.
     * Returns the first instance available.
     */
    @SuppressLint("RestrictedApi")
    fun findFirstWatchNextProgram(context: Context, predicate: (Cursor) -> Boolean):
            Pair<WatchNextProgram?, Long?> {
        val COLUMN_WATCH_NEXT_ID_INDEX = 0

        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    if (predicate(cursor)) {
                        return fromCursor(cursor) to cursor.getLong(COLUMN_WATCH_NEXT_ID_INDEX)
                    }
                } while (it.moveToNext())
            }
        }
        return null to null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("Range")
    @Synchronized
    private fun getWatchNextProgramByVideoId(
        id: String,
        context: Context
    ): Pair<WatchNextProgram?, Long?> {
        return findFirstWatchNextProgram(context) { cursor ->
            (cursor.getString(cursor.getColumnIndex(COLUMN_INTERNAL_PROVIDER_ID)) == id)
        }
    }

    /** Prevents losing data when removing and adding simultaneously */
    private val continueWatchingLock = Mutex()

    @SuppressLint("RestrictedApi")
    @Throws
    @WorkerThread
    suspend fun Context.addProgramsToContinueWatching(data: List<ResumeWatchingResult>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val context = this
        continueWatchingLock.withLock {
            val timeStampHashMap = HashMap<Int, ResumeWatching>()
            getAllResumeStateIds()?.forEach { id ->
                val lastWatched = getLastWatched(id) ?: return@forEach
                timeStampHashMap[lastWatched.parentId] = lastWatched
            }

            val currentProgramIds = data.mapNotNull { episodeInfo ->
                try {
                    val customId = "${episodeInfo.id}|${episodeInfo.apiName}|${episodeInfo.url}"
                    val (program, id) = getWatchNextProgramByVideoId(customId, context)
                    val nextProgram = buildWatchNextProgramUri(
                        context,
                        episodeInfo,
                        timeStampHashMap[episodeInfo.id]
                    )

                    if (program != null && id != null) {
                        PreviewChannelHelper(context).updateWatchNextProgram(
                            nextProgram,
                            id,
                        )
                        id
                    } else {
                        PreviewChannelHelper(context)
                            .publishWatchNextProgram(nextProgram)
                    }
                } catch (e: Exception) {
                    logError(e)
                    null
                }
            }.toSet()

            val allOldPrograms = getAllWatchNextPrograms(context) - currentProgramIds

            allOldPrograms.forEach {
                context.contentResolver.delete(
                    TvContractCompat.buildWatchNextProgramUri(it),
                    null, null
                )
            }
        }
    }

    /** Sort subtitles by names */
    fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
        return subs
            .sortedWith(
                compareBy { subtitle: SubtitleData -> subtitle.originalName }
                    .thenBy { subtitle: SubtitleData -> subtitle.nameSuffix })
    }

    fun Context.getApiSettings(): HashSet<String> {
        val hashSet = HashSet<String>()
        val activeLangs = getApiProviderLangSettings()
        val hasUniversal = activeLangs.contains(AllLanguagesName)
        hashSet.addAll(apis.filter { hasUniversal || activeLangs.contains(it.lang) }
            .map { it.name })
        return hashSet
    }

    fun Context.getApiDubstatusSettings(): HashSet<DubStatus> {
        val hashSet = HashSet<DubStatus>()
        hashSet.addAll(DubStatus.values())
        val list = AppPreferenceManager.getStringSetSync(
            AppPreferenceManager.KEY_DISPLAY_SUB,
            hashSet.map { it.name }.toSet()
        ) ?: return hashSet

        val names = DubStatus.values().map { it.name }.toHashSet()
        return list.filter { names.contains(it) }.map { DubStatus.valueOf(it) }.toHashSet()
    }

    fun Context.getApiProviderLangSettings(): HashSet<String> {
        val hashSet = hashSetOf(AllLanguagesName)
        val list = AppPreferenceManager.getStringSetSync(
            AppPreferenceManager.KEY_PROVIDER_LANG,
            hashSet
        )

        if (list.isNullOrEmpty()) return hashSet
        return list.toHashSet()
    }

    fun Context.getApiTypeSettings(): HashSet<TvType> {
        val hashSet = HashSet<TvType>()
        hashSet.addAll(TvType.values())
        val list = AppPreferenceManager.getStringSetSync(
            AppPreferenceManager.KEY_SEARCH_TYPES,
            hashSet.map { it.name }.toSet()
        )

        if (list.isNullOrEmpty()) return hashSet

        val names = TvType.values().map { it.name }.toHashSet()
        val realSet = list.filter { names.contains(it) }.map { TvType.valueOf(it) }.toHashSet()
        if (realSet.isEmpty()) return hashSet

        return realSet
    }

    fun Context.updateHasTrailers() {
        LoadResponse.isTrailersEnabled = getHasTrailers()
    }

    private fun Context.getHasTrailers(): Boolean {
        return AppPreferenceManager.getBooleanSync(AppPreferenceManager.KEY_SHOW_TRAILERS, true)
    }

    fun Context.shouldShowPlayerMetadata(): Boolean {
        return AppPreferenceManager.getBooleanSync(
            AppPreferenceManager.KEY_SHOW_PLAYER_METADATA,
            true
        )
    }

    fun Context.filterProviderByPreferredMedia(hasHomePageIsRequired: Boolean = true): List<MainAPI> {
        val oldLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = TvType::class.java.classLoader

        val default = TvType.values()
            .sorted()
            .filter { it != TvType.NSFW }
            .map { it.ordinal }

        Thread.currentThread().contextClassLoader = oldLoader

        val defaultSet = default.map { it.toString() }.toSet()
        val currentPrefMedia = try {
            AppPreferenceManager.getStringSetSync(AppPreferenceManager.KEY_PREFER_MEDIA_TYPE, defaultSet)
                ?.mapNotNull { it.toIntOrNull() }
        } catch (e: Throwable) {
            null
        } ?: default
        val langs = this.getApiProviderLangSettings()
        val hasUniversal = langs.contains(AllLanguagesName)
        val allApis =
            apis.filter { api -> (hasUniversal || langs.contains(api.lang)) && (api.hasMainPage || !hasHomePageIsRequired) }
        return if (currentPrefMedia.isEmpty()) {
            allApis
        } else {
            allApis.filter { api -> api.supportedTypes.any { currentPrefMedia.contains(it.ordinal) } }
        }
    }

    fun Context.filterSearchResultByFilmQuality(data: List<SearchResponse>): List<SearchResponse> {
        if (data.isNotEmpty()) {
            val filteredSearchQuality = AppPreferenceManager.getStringSetSync(AppPreferenceManager.KEY_FILTER_SEARCH_QUALITY, setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull()
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return data.filter { item ->
                    val searchQualVal = item.quality?.ordinal ?: -1
                    !filteredSearchQuality.contains(searchQualVal)
                }
            }
        }
        return data
    }

    fun Context.filterHomePageListByFilmQuality(data: HomePageList): HomePageList {
        if (data.list.isNotEmpty()) {
            val filteredSearchQuality = AppPreferenceManager.getStringSetSync(AppPreferenceManager.KEY_FILTER_SEARCH_QUALITY, setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull()
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return HomePageList(
                    name = data.name,
                    isHorizontalImages = data.isHorizontalImages,
                    list = data.list.filter { item ->
                        val searchQualVal = item.quality?.ordinal ?: -1
                        !filteredSearchQuality.contains(searchQualVal)
                    }
                )
            }
        }
        return data
    }

    fun openWebView(fragment: Fragment?, url: String) {
        fragment?.context?.openBrowser(url)
    }

    /**
     * If fallbackWebview is true and a fragment is supplied then it will open a webview with the url if the browser fails.
     */
    fun Context.openBrowser(
        url: String,
        fallbackWebview: Boolean = false,
        fragment: Fragment? = null,
    ) {
        val runBlock = {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = url.toUri()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                val activityResultRegistry = fragment?.activity?.activityResultRegistry
                if (activityResultRegistry != null) {
                    activityResultRegistry.register(
                        url,
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_CANCELED && fallbackWebview) {
                            openWebView(fragment, url)
                        }
                    }.launch(intent)
                } else this.startActivity(intent)
            } catch (e: Exception) {
                logError(e)
                if (fallbackWebview) {
                    openWebView(fragment, url)
                }
            }
        }

        val activity = this as? Activity ?: fragment?.activity
        if (activity != null) {
            activity.runOnUiThread(runBlock)
        } else {
            runBlock()
        }
    }

    fun openBrowser(url: String, fallbackWebView: Boolean = false, fragment: Fragment? = null) {
        (com.lagradost.api.getContext() as? Context)?.openBrowser(url, fallbackWebView, fragment)
    }

    fun openBrowser(url: String, activity: FragmentActivity?) {
        openBrowser(
            url,
            Globals.isLayout(Globals.TV or Globals.EMULATOR),
            activity?.supportFragmentManager?.fragments?.lastOrNull()
        )
    }

    fun Context.isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    @OptIn(com.lagradost.cloudstream3.Prerelease::class)
    fun splitQuery(url: java.net.URL): Map<String, String> {
        return com.lagradost.cloudstream3.splitUrlParameters(url.toString())
    }

    fun Context.getNameFull(name: String?, episode: Int?, season: Int?): String {
        val rEpisode = if (episode == 0) null else episode
        val rSeason = if (season == 0) null else season

        val seasonName = txt(Res.string.season).asString(this)
        val episodeName = txt(Res.string.episode).asString(this)
        val seasonNameShort = txt(Res.string.season_short).asString(this)
        val episodeNameShort = txt(Res.string.episode_short).asString(this)

        if (name != null) {
            return if (rEpisode != null && rSeason != null) {
                "$seasonNameShort${rSeason}:$episodeNameShort${rEpisode} $name"
            } else if (rEpisode != null) {
                "$episodeName $rEpisode. $name"
            } else {
                name
            }
        } else {
            if (rEpisode != null && rSeason != null) {
                return "$seasonName $rSeason - $episodeName $rEpisode"
            } else if (rSeason == null) {
                return "$episodeName $rEpisode"
            }
        }
        return ""
    }

    fun Context.getShortSeasonText(episode: Int?, season: Int?): String? {
        val rEpisode = if (episode == 0) null else episode
        val rSeason = if (season == 0) null else season
        val seasonNameShort = txt(Res.string.season_short).asString(this)
        val episodeNameShort = txt(Res.string.episode_short).asString(this)
        return if (rEpisode != null && rSeason != null) {
            "$seasonNameShort${rSeason}:$episodeNameShort${rEpisode}"
        } else if (rEpisode != null) {
            "$episodeNameShort$rEpisode"
        } else null
    }

    fun loadResult(
        url: String,
        apiName: String,
        name: String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
    }

    fun FragmentActivity.loadResult(
        url: String,
        apiName: String,
        name: String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
        try {
            Kitsu.isEnabled =
                AppPreferenceManager.getBooleanSync(AppPreferenceManager.KEY_SHOW_KITSU_POSTERS, true)
        } catch (t: Throwable) {
            logError(t)
        }

        this.runOnUiThread {
        }
    }

    fun loadSearchResult(
        card: SearchResponse,
        startAction: Int = 0,
        startValue: Int? = null,
    ) {
    }

    fun Activity?.loadSearchResult(
        card: SearchResponse,
        startAction: Int = 0,
        startValue: Int? = null,
    ) {
        this?.runOnUiThread {
        }
    }

    fun Activity.requestLocalAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                Log.e("TAG", "focusRequest was null")
                return
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private var currentAudioFocusRequest: AudioFocusRequest? = null
    private var currentAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    var onAudioFocusEvent = Event<Boolean>()

    private fun getAudioListener(): AudioManager.OnAudioFocusChangeListener? {
        if (currentAudioFocusChangeListener != null) return currentAudioFocusChangeListener
        currentAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
                    else -> true
                }
            )
        }
        return currentAudioFocusChangeListener
    }

    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
        if (!Globals.isLayout(Globals.TV or Globals.EMULATOR)) return
        this.getButton(buttonFocus)?.run {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    fun Context.setDefaultFocus() {
    }

    fun Context.isUsingMobileData(): Boolean {
        val connectionManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork: Network? = connectionManager.activeNetwork
            val networkCapabilities = connectionManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                    !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            connectionManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }

    fun Context.isAppInstalled(uri: String): Boolean {
        return try {
            packageManager.getPackageInfo(uri, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getFocusRequest(): AudioFocusRequest? {
        if (currentAudioFocusRequest != null) return currentAudioFocusRequest
        currentAudioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                getAudioListener()?.let {
                    setOnAudioFocusChangeListener(it)
                }
                build()
            }
        } else null
        return currentAudioFocusRequest
    }

    private val cachedBitmaps = ConcurrentHashMap<String, Bitmap>()

    fun Context.getImageBitmapFromUrl(
        url: String,
        headers: Map<String, String>? = null
    ): Bitmap? = safe {
        cachedBitmaps[url]?.let {
            return@safe it
        }

        val imageLoader = SingletonImageLoader.get(this)

        val request = ImageRequest.Builder(this)
            .data(url)
            .apply {
                headers?.forEach { (key, value) ->
                    extras[Extras.Key<String>(key)] = value
                }
            }
            .build()

        val bitmap = runBlocking {
            val result = imageLoader.execute(request)
            (result as? SuccessResult)?.image?.asDrawable(applicationContext.resources)
                ?.toBitmap()
        }

        bitmap?.let {
            cachedBitmaps.putIfAbsent(url, it)
        }

        return@safe bitmap
    }
}
