package ru.student.drivenext.presentation

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import ru.student.drivenext.R
import ru.student.drivenext.data.AndroidNetworkRepository
import ru.student.drivenext.data.PreferencesOnboardingRepository
import ru.student.drivenext.data.PreferencesSessionRepository
import ru.student.drivenext.data.SupabaseAuthRepository
import ru.student.drivenext.domain.CheckInternetUseCase
import ru.student.drivenext.domain.ResolveDestinationUseCase
import java.io.File

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var container: FrameLayout
    private lateinit var authScreens: AuthScreens
    private var renderedScreen: Screen? = null
    private var photoKind: PhotoKind? = null
    private var pendingCameraUri: Uri? = null
    private lateinit var formBackCallback: OnBackPressedCallback
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photoKind?.let { viewModel.setPhoto(it, uri.toString()) }
        }
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoKind?.let { kind -> pendingCameraUri?.let { viewModel.setPhoto(kind, it.toString()) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.screenContainer)
        photoKind = savedInstanceState?.getString("photo_kind")?.let { PhotoKind.valueOf(it) }
        pendingCameraUri = savedInstanceState?.getString("camera_uri")?.let(Uri::parse)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val network = AndroidNetworkRepository(applicationContext)
        val onboarding = PreferencesOnboardingRepository(applicationContext)
        val session = PreferencesSessionRepository(applicationContext)
        val auth = SupabaseAuthRepository(applicationContext, session)
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(
                network, onboarding, session, auth,
                CheckInternetUseCase(network),
                ResolveDestinationUseCase(onboarding, session)
            )
        )[MainViewModel::class.java]
        authScreens = AuthScreens(this, viewModel, ::choosePhoto) {
            viewModel.startGoogleSignIn { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        formBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { authScreens.handleBack() }
        }
        onBackPressedDispatcher.addCallback(this, formBackCallback)
        viewModel.state.observe(this, ::render)
        viewModel.message.observe(this) { message ->
            if (message != null) {
                android.app.AlertDialog.Builder(this).setMessage(message).setPositiveButton("ОК", null).show()
                viewModel.consumeMessage()
            }
        }
        viewModel.start()
        intent?.data?.takeIf { it.scheme == "drivenext" }?.let { viewModel.handleGoogleCallback(it.toString()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.takeIf { it.scheme == "drivenext" }?.let { viewModel.handleGoogleCallback(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        photoKind?.let { outState.putString("photo_kind", it.name) }
        pendingCameraUri?.let { outState.putString("camera_uri", it.toString()) }
        super.onSaveInstanceState(outState)
    }

    private fun render(state: AppUiState) {
        formBackCallback.isEnabled = state.screen in setOf(Screen.LOGIN, Screen.REGISTER_ONE,
            Screen.REGISTER_TWO, Screen.REGISTER_THREE)
        if (renderedScreen != state.screen) {
            renderedScreen = state.screen
            val layout = when (state.screen) {
                Screen.SPLASH -> R.layout.screen_splash
                Screen.OFFLINE -> R.layout.screen_offline
                Screen.ONBOARDING -> R.layout.screen_onboarding
                else -> null
            }
            container.removeAllViews()
            if (layout == null) container.addView(authScreens.create(state.screen))
            else LayoutInflater.from(this).inflate(layout, container, true)
            when (state.screen) {
                Screen.SPLASH -> Unit
                Screen.OFFLINE -> container.findViewById<Button>(R.id.retryButton)
                    .setOnClickListener { viewModel.retryConnection() }
                Screen.ONBOARDING -> setupOnboarding()
                else -> Unit
            }
        }
        if (state.screen == Screen.REGISTER_THREE) authScreens.refreshPhotos()
        findViewById<View>(R.id.loadingOverlay).visibility = if (state.busy) View.VISIBLE else View.GONE
        if (state.screen == Screen.OFFLINE) {
            val button = container.findViewById<Button>(R.id.retryButton)
            val progress = container.findViewById<ProgressBar>(R.id.retryProgress)
            button.isEnabled = !state.checkingConnection
            button.text = if (state.checkingConnection) "" else getString(R.string.retry)
            progress.visibility = if (state.checkingConnection) View.VISIBLE else View.GONE
        }
    }

    private fun choosePhoto(kind: PhotoKind) {
        photoKind = kind
        android.app.AlertDialog.Builder(this)
            .setItems(arrayOf("Выбрать из галереи", "Сделать фото")) { _, selected ->
                if (selected == 0) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    val directory = File(cacheDir, "registration_photos").apply { mkdirs() }
                    val file = File.createTempFile("photo_", ".jpg", directory)
                    pendingCameraUri = FileProvider.getUriForFile(this,
                        "$packageName.fileprovider", file)
                    takePhoto.launch(requireNotNull(pendingCameraUri))
                }
            }.show()
    }

    private fun setupOnboarding() {
        val pager = container.findViewById<ViewPager2>(R.id.onboardingPager)
        val next = container.findViewById<Button>(R.id.nextButton)
        val indicators = container.findViewById<LinearLayout>(R.id.pageIndicators)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int) = OnboardingSlideFragment.newInstance(position)
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                next.setText(if (position == 2) R.string.go else R.string.next)
                updateIndicators(indicators, position)
            }
        })
        updateIndicators(indicators, 0)
        next.setOnClickListener {
            if (pager.currentItem == 2) viewModel.finishOnboarding()
            else pager.setCurrentItem(pager.currentItem + 1, true)
        }
        container.findViewById<TextView>(R.id.skipButton)
            .setOnClickListener { viewModel.finishOnboarding() }
    }

    private fun updateIndicators(row: LinearLayout, selected: Int) {
        row.removeAllViews()
        repeat(3) { index ->
            val dot = View(this)
            val width = if (index == selected) 30 else 9
            val params = LinearLayout.LayoutParams(dp(width), dp(7))
            params.marginEnd = dp(7)
            dot.layoutParams = params
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(getColor(if (index == selected) R.color.ink else R.color.soft))
            }
            row.addView(dot)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
