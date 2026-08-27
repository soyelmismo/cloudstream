package com.lagradost.cloudstream3.utils

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ListAdapter
import android.widget.ListView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Globals.EMULATOR
import com.lagradost.cloudstream3.utils.Globals.PHONE
import com.lagradost.cloudstream3.utils.Globals.TV
import com.lagradost.cloudstream3.utils.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.isRtl
import kotlin.math.roundToInt

object UIHelper {
    val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
    val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
    val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)
    val Float.toPxInt: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    fun Context.checkWrite(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
                == PackageManager.PERMISSION_GRANTED
                // Since Android 13, we can't request external storage permission,
                // so don't check it.
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    }

    fun Activity.requestRW() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ),
            1337
        )
    }

    fun clipboardHelper(label: UiText, text: CharSequence) {
        val ctx = com.lagradost.api.getContext() as? Context ?: return
        try {
            val clip = ClipData.newPlainText(label.asString(ctx), text)
            ctx.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    fun clipboardHelper(context: Context?, label: UiText, text: CharSequence) {
        val ctx = context ?: (com.lagradost.api.getContext() as? Context) ?: return
        try {
            val clip = ClipData.newPlainText(label.asString(ctx), text)
            ctx.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /**
     * Sets ListView height dynamically based on the height of the items.
     */
    fun setListViewHeightBasedOnItems(listView: ListView?) {
        val listAdapter: ListAdapter = listView?.adapter ?: return
        val numberOfItems: Int = listAdapter.count

        var totalItemsHeight = 0
        for (itemPos in 0 until numberOfItems) {
            val item: View = listAdapter.getView(itemPos, null, listView)
            item.measure(0, 0)
            totalItemsHeight += item.measuredHeight
        }

        val totalDividersHeight: Int = listView.dividerHeight *
                (numberOfItems - 1)

        val params: ViewGroup.LayoutParams = listView.layoutParams
        params.height = totalItemsHeight + totalDividersHeight
        listView.layoutParams = params
        listView.requestLayout()
    }

    fun Context.getSpanCount(isHorizontal: Boolean = false): Int {
        val spanCountLandscape = if (isHorizontal) 3 else 6
        val spanCountPortrait = if (isHorizontal) 2 else 3
        val orientation = resources.configuration.orientation

        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else spanCountPortrait
    }

    fun hideKeyboard(view: View?) {
        if (view == null) return

        val inputMethodManager =
            view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun Activity.hideKeyboard() {
        window?.decorView?.clearFocus()
        this.findViewById<View>(android.R.id.content)?.rootView?.let {
            hideKeyboard(it)
        }
    }

    fun Context.openActivity(activity: Class<*>, args: Bundle? = null, baseIntent: Intent? = null) {
        val tag = "NavComponent"
        try {
            val intent = baseIntent ?: Intent()
            intent.setClass(this, activity)

            if (args != null) {
                intent.putExtras(args)
            }
            Log.i(tag, "Navigating to Activity: ${activity.simpleName}")
            startActivity(intent)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    @ColorInt
    fun Context.getResourceColor(@AttrRes resource: Int, alphaFactor: Float = 1f): Int {
        val color = colorFromAttribute(resource)
        return if (alphaFactor < 1f) adjustAlpha(color, alphaFactor) else color
    }

    @ColorInt
    fun Context.colorFromAttribute(@AttrRes attribute: Int): Int {
        var color = 0
        withStyledAttributes(attrs = intArrayOf(attribute)) {
            color = getColor(0, 0)
        }
        return color
    }

    @ColorInt
    fun adjustAlpha(@ColorInt color: Int, factor: Float): Int {
        val alpha = (color.alpha * factor).roundToInt()
        return Color.argb(alpha, color.red, color.green, color.blue)
    }

    fun Activity.hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            return
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    fun Activity.enableEdgeToEdgeCompat() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        WindowCompat.enableEdgeToEdge(window)
    }

    fun Activity.setNavigationBarColorCompat(@AttrRes resourceId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return

        @Suppress("DEPRECATION")
        window?.navigationBarColor = colorFromAttribute(resourceId)
    }

    fun Context.getStatusBarHeight(): Int {
        if (isLayout(TV or EMULATOR)) {
            return 0
        }

        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun fixPaddingStatusbarMargin(v: View?) {
        if (v == null) return
        val ctx = v.context ?: return

        v.layoutParams = v.layoutParams.apply {
            if (this is MarginLayoutParams) {
                setMargins(
                    v.marginLeft,
                    v.marginTop + ctx.getStatusBarHeight(),
                    v.marginRight,
                    v.marginBottom
                )
            }
        }
    }

    fun fixPaddingStatusbarView(v: View?) {
        if (v == null) return
        val ctx = v.context ?: return
        val params = v.layoutParams
        params.height = ctx.getStatusBarHeight()
        v.layoutParams = params
    }

    fun fixSystemBarsPadding(
        v: View,
        @DimenRes heightResId: Int? = null,
        @DimenRes widthResId: Int? = null,
        padTop: Boolean = true,
        padBottom: Boolean = true,
        padLeft: Boolean = true,
        padRight: Boolean = true,
        overlayCutout: Boolean = true,
        fixIme: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (padTop) {
                val ctx = v.context ?: return
                v.updatePadding(top = ctx.getStatusBarHeight())
            }
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(v) { view, windowInsets ->
            val leftCheck = if (view.isRtl()) padRight else padLeft
            val rightCheck = if (view.isRtl()) padLeft else padRight

            val insetTypes = WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                if (fixIme) WindowInsetsCompat.Type.ime() else 0

            val insets = windowInsets.getInsets(insetTypes)

            view.updatePadding(
                left = if (leftCheck) insets.left else view.paddingLeft,
                right = if (rightCheck) insets.right else view.paddingRight,
                bottom = if (padBottom) insets.bottom else view.paddingBottom,
                top = if (padTop) insets.top else view.paddingTop
            )

            heightResId?.let {
                val heightPx = view.resources.getDimensionPixelSize(it)
                view.updateLayoutParams {
                    height = heightPx + insets.bottom
                }
            }

            widthResId?.let {
                val widthPx = view.resources.getDimensionPixelSize(it)
                view.updateLayoutParams {
                    val startInset = if (view.isRtl()) insets.right else insets.left
                    width = if (startInset > 0) widthPx + startInset else widthPx
                }
            }

            if (overlayCutout && isLayout(PHONE)) {
                val cutout = windowInsets.displayCutout
                if (cutout != null) {
                    val left = if (!leftCheck) 0 else cutout.safeInsetLeft
                    val right = if (!rightCheck) 0 else cutout.safeInsetRight
                    view.overlay.clear()
                    if (left > 0 || right > 0) {
                        view.overlay.add(
                            CutoutOverlayDrawable(
                                view,
                                leftCutout = left,
                                rightCutout = right
                            )
                        )
                    }
                }
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    fun Context.getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Context?.isBottomLayout(): Boolean {
        if (this == null) return true
        return com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager.getBooleanSync(
            com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager.KEY_BOTTOM_TITLE,
            true
        )
    }

    fun Activity.changeStatusBarState(hide: Boolean) {
        try {
            if (hide) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    fun Activity.showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isLayout(EMULATOR)) {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        changeStatusBarState(isLayout(EMULATOR))
    }

    fun showInputMethod(view: View?) {
        if (view == null) return
        val inputMethodManager =
            view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.showSoftInput(view, 0)
    }

    fun Dialog?.dismissSafe(activity: Activity? = null) {
        if (this?.isShowing == true && (activity == null || !activity.isFinishing)) {
            this.dismiss()
        }
    }
}

private class CutoutOverlayDrawable(
    private val view: View,
    private val leftCutout: Int,
    private val rightCutout: Int,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        if (leftCutout > 0) canvas.drawRect(
            0f,
            0f,
            leftCutout.toFloat(),
            view.height.toFloat(),
            paint
        )
        if (rightCutout > 0) {
            canvas.drawRect(
                view.width - rightCutout.toFloat(),
                0f, view.width.toFloat(),
                view.height.toFloat(),
                paint
            )
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.OPAQUE
}
