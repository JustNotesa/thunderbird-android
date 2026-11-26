package com.fsck.k9.ui.base

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.asLiveData
import com.fsck.k9.controller.push.PushController
import com.fsck.k9.ui.base.onehanded.OneHandedModeSetting
import com.fsck.k9.ui.base.onehanded.ThumbZoneController
import java.util.Locale
import kotlin.math.max
import net.thunderbird.core.ui.theme.api.Theme
import net.thunderbird.core.ui.theme.manager.ThemeManager
import org.koin.android.ext.android.inject

abstract class BaseActivity(
    private val themeType: ThemeType,
) : AppCompatActivity() {
    constructor() : this(ThemeType.DEFAULT)

    private val pushController: PushController by inject()
    protected val themeManager: ThemeManager by inject()
    private val appLanguageManager: AppLanguageManager by inject()

    private var overrideLocaleOnLaunch: Locale? = null

    override fun attachBaseContext(baseContext: Context) {
        overrideLocaleOnLaunch = appLanguageManager.getOverrideLocale()

        val newBaseContext = overrideLocaleOnLaunch?.let { locale ->
            LocaleContextWrapper(baseContext, locale)
        } ?: baseContext

        super.attachBaseContext(newBaseContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initializeTheme()
        initializePushController()

        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = themeManager.appTheme == Theme.LIGHT
        }

        super.onCreate(savedInstanceState)

        setLayoutDirection()
        listenForAppLanguageChanges()
    }

    // On Android 12+ the layout direction doesn't seem to be updated when recreating the activity. This is a problem
    // when switching from an LTR to an RTL language (or the other way around) using the language picker in the app.
    private fun setLayoutDirection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.decorView.layoutDirection = resources.configuration.layoutDirection
        }
    }

    private fun listenForAppLanguageChanges() {
        appLanguageManager.overrideLocale.asLiveData().observe(this) { overrideLocale ->
            if (overrideLocale != overrideLocaleOnLaunch) {
                recreateCompat()
            }
        }
    }

    private fun initializeTheme() {
        val theme = when (themeType) {
            ThemeType.DEFAULT -> themeManager.appThemeResourceId
            ThemeType.DIALOG -> themeManager.translucentDialogThemeResourceId
        }
        setTheme(theme)
    }

    private fun initializePushController() {
        pushController.init()
    }

    /**
     * When true, setLayout() pads the app bar for the navigation bar and the content for the status bar, because the
     * app bar sits at the bottom.
     */
    protected open val isAppBarAtBottom: Boolean get() = isOneHandedModeActive

    /**
     * Screens that take part in the one-handed mode return `true`. With the mode switched on, [setLayout] then moves
     * their toolbar to the bottom edge and shows a header with the title above the content (the thumb zone layout).
     * The screen needs the layout contract of [setLayout] with a `CoordinatorLayout` as its root.
     */
    protected open val supportsOneHandedMode: Boolean get() = false

    private val oneHandedModeSetting: OneHandedModeSetting by inject()
    private val isOneHandedModeActive: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        supportsOneHandedMode && oneHandedModeSetting.isEnabled()
    }
    private var thumbZone: ThumbZoneController? = null

    /**
     * Sets title and explanation of the thumb zone header explicitly instead of taking them from the toolbar. Does
     * nothing when the one-handed mode is off.
     */
    protected fun setThumbZoneTexts(title: CharSequence?, description: CharSequence?) {
        thumbZone?.setTexts(title, description)
    }

    /** Whether this screen shows the toolbar at the bottom because of the one-handed mode. */
    protected val isInOneHandedMode: Boolean get() = thumbZone != null

    /**
     * Sets the activity's content view to the specified layout resource and configures
     * the AppBarLayout and Toolbar for edge-to-edge display.
     *
     * It expects the layout to contain the following views with the specified IDs:
     * - A ViewGroup with ID `R.id.coordinator_layout` as the root layout.
     * - An AppBarLayout with ID `R.id.app_bar_layout` for the top app bar.
     * - A Toolbar with ID `R.id.toolbar` to be set as the support action bar.
     * - A ViewGroup with ID `R.id.content_container` to hold the main content of the activity or fragment.
     *
     * When [isAppBarAtBottom] is `true` the app bar is at the bottom of the screen, so the insets are applied to the
     * opposite sides.
     *
     * @param layoutResId The resource ID of the layout to be set as the content view.
     * @throws IllegalStateException if the required AppBarLayout or Toolbar is not found.
     */
    protected fun setLayout(@LayoutRes layoutResId: Int) {
        setContentView(layoutResId)

        val coordinatorLayout = findViewById<ViewGroup>(R.id.coordinator_layout)
            ?: error("TB layouts must provide a ViewGroup with id='coordinator_layout'.")
        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar_layout)
            ?: error("TB layouts must provide an AppBarLayout with id='app_bar_layout'.")
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
            ?: error("TB layouts must provide a Toolbar with id='toolbar'.")
        val contentContainer = findViewById<ViewGroup>(R.id.content_container)
            ?: error("TB layouts must provide a FrameLayout with id='content_container'.")

        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(coordinatorLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                systemBars() or displayCutout(),
            )
            val imeInsets = windowInsets.getInsets(ime())

            if (isAppBarAtBottom) {
                appBarLayout.updatePadding(
                    top = 0,
                    left = insets.left,
                    right = insets.right,
                    bottom = max(insets.bottom, imeInsets.bottom),
                )

                contentContainer.updatePadding(
                    top = insets.top,
                    left = insets.left,
                    right = insets.right,
                    bottom = 0,
                )
            } else {
                appBarLayout.updatePadding(
                    top = insets.top,
                    left = insets.left,
                    right = insets.right,
                    bottom = 0,
                )

                contentContainer.updatePadding(
                    top = 0,
                    left = insets.left,
                    right = insets.right,
                    bottom = max(insets.bottom, imeInsets.bottom),
                )
            }

            hideActionModeStatusGuard()

            WindowInsetsCompat.CONSUMED
        }

        if (isOneHandedModeActive && coordinatorLayout is CoordinatorLayout) {
            setUpOneHandedLayout(appBarLayout, toolbar, contentContainer)
        }
    }

    /**
     * Moves the app bar to the bottom edge of the coordinator layout, with the content ending above it, and adds the
     * header for the thumb zone layout. The insets are already handled by [setLayout] for an app bar at the bottom.
     */
    private fun setUpOneHandedLayout(
        appBarLayout: com.google.android.material.appbar.AppBarLayout,
        toolbar: Toolbar,
        contentContainer: ViewGroup,
    ) {
        (appBarLayout.layoutParams as CoordinatorLayout.LayoutParams).gravity = Gravity.BOTTOM

        val contentParams = contentContainer.layoutParams as CoordinatorLayout.LayoutParams
        contentParams.behavior = null
        contentContainer.layoutParams = contentParams

        // The height of the app bar includes the padding for the navigation bar or the keyboard.
        appBarLayout.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val params = contentContainer.layoutParams as CoordinatorLayout.LayoutParams
            if (params.bottomMargin != view.height) {
                params.bottomMargin = view.height
                contentContainer.layoutParams = params
            }
        }

        val controller = ThumbZoneController(contentContainer)
        thumbZone = controller

        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            controller.syncFromToolbar(supportActionBar?.title, supportActionBar?.subtitle)
        }
        controller.syncFromToolbar(supportActionBar?.title, supportActionBar?.subtitle)

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, state: Bundle?) {
                    if (f !is DialogFragment) controller.attach(v)
                }

                override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                    controller.detach(f.view)
                }
            },
            true,
        )

        // Screens that have their list in the layout itself, not in a fragment
        controller.attach(contentContainer)
    }

    /**
     * Hides the status bar guard AppCompat draws while a contextual action mode is visible.
     *
     * With `windowActionModeOverlay` enabled, AppCompat offsets the contextual action bar below the status bar and
     * fills the space that is left over with an opaque view. It picks the colour of that view from the deprecated
     * `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR` flag. On API 35 and above nothing sets that flag any more, because
     * `WindowInsetsControllerCompat` then applies the appearance only through `setSystemBarsAppearance()` instead of
     * mirroring it onto the old flag, so the guard always ends up black and hides the clock and the status icons.
     * This window is drawn edge-to-edge and the AppBarLayout behind the guard already covers that area, so the guard
     * just needs to get out of the way.
     *
     * AppCompat updates the guard from its own insets listener on the sub decor, which runs before the insets reach the
     * layout installed by [setLayout]. Doing this from there means the guard is cleared again every time AppCompat
     * recolours it.
     */
    private fun hideActionModeStatusGuard() {
        val subDecor = findViewById<View>(android.R.id.content)?.parent as? ViewGroup ?: return

        // AppCompat adds the guard as an id-less, plain View child of the sub decor. The children it puts there
        // otherwise are the content frame and the contextual action bar, which are both ViewGroups with an id.
        // If that ever stops being unambiguous, leave every one of them alone rather than recolour a guess.
        subDecor.children
            .singleOrNull { it.javaClass == View::class.java && it.id == View.NO_ID }
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    protected fun recreateCompat() {
        ActivityCompat.recreate(this)
    }
}

enum class ThemeType {
    DEFAULT,
    DIALOG,
}
