package ru.student.drivenext.presentation

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.student.drivenext.domain.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class Screen { SPLASH, OFFLINE, ONBOARDING, AUTH, LOGIN, REGISTER_ONE, REGISTER_TWO, REGISTER_THREE, SUCCESS, HOME }
enum class PhotoKind { AVATAR, LICENSE, PASSPORT }

data class AppUiState(val screen: Screen = Screen.SPLASH, val checkingConnection: Boolean = false,
    val busy: Boolean = false, val revision: Int = 0)

class MainViewModel(
    private val network: NetworkRepository,
    private val onboarding: OnboardingRepository,
    private val session: SessionRepository,
    private val auth: AuthRepository,
    private val checkInternet: CheckInternetUseCase,
    private val resolveDestination: ResolveDestinationUseCase
) : ViewModel() {
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableLiveData(AppUiState())
    val state: LiveData<AppUiState> = _state
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message
    var registration = RegistrationData()
        private set
    var termsAccepted = false
        private set
    private var started = false
    private var splashFinished = false
    private var interruptedScreen = Screen.SPLASH

    fun start() {
        if (started) return
        started = true
        network.observe(::onNetworkChanged)
        handler.postDelayed({
            splashFinished = true
            if (checkInternet()) {
                if (_state.value?.screen == Screen.SPLASH) showDestination()
                else if (_state.value?.screen == Screen.OFFLINE) restoreAfterConnection()
            } else showOffline()
        }, 2500)
    }

    fun consumeMessage() { _message.value = null }

    private fun onNetworkChanged(online: Boolean) {
        if (!online && !splashFinished && _state.value?.screen == Screen.SPLASH) return
        if (!online) showOffline()
        else if (_state.value?.screen == Screen.OFFLINE) restoreAfterConnection()
    }

    private fun showOffline() {
        val current = _state.value?.screen ?: Screen.SPLASH
        if (current != Screen.OFFLINE) interruptedScreen = current
        _state.value = AppUiState(Screen.OFFLINE)
    }

    private fun restoreAfterConnection() {
        _state.value = AppUiState(interruptedScreen)
        if (interruptedScreen == Screen.SPLASH && splashFinished) showDestination()
    }

    private fun showDestination() {
        viewModelScope.launch {
            if (!session.hasValidAccessToken() && session.hasRefreshToken()) auth.refreshSession()
            val screen = when (resolveDestination()) {
                Destination.ONBOARDING -> Screen.ONBOARDING
                Destination.AUTH -> Screen.AUTH
                Destination.HOME -> Screen.HOME
            }
            if (_state.value?.screen == Screen.SPLASH) _state.value = AppUiState(screen)
        }
    }

    fun retryConnection() {
        if (_state.value?.screen != Screen.OFFLINE) return
        _state.value = AppUiState(Screen.OFFLINE, checkingConnection = true)
        handler.postDelayed({
            if (_state.value?.screen == Screen.OFFLINE) {
                if (checkInternet()) restoreAfterConnection()
                else _state.value = AppUiState(Screen.OFFLINE)
            }
        }, 450)
    }

    fun finishOnboarding() { onboarding.complete(); navigate(Screen.AUTH) }
    fun showLogin() = navigate(Screen.LOGIN)
    fun showRegistration() = navigate(Screen.REGISTER_ONE)
    fun showAuthChoice() = navigate(Screen.AUTH)

    fun back() {
        when (_state.value?.screen) {
            Screen.LOGIN, Screen.REGISTER_ONE -> navigate(Screen.AUTH)
            Screen.REGISTER_TWO -> navigate(Screen.REGISTER_ONE)
            Screen.REGISTER_THREE -> navigate(Screen.REGISTER_TWO)
            else -> Unit
        }
    }

    fun submitLogin(email: String, password: String) {
        if (!validEmail(email)) return showError("Введите корректный адрес электронной почты.")
        if (password.isBlank()) return showError("Введите пароль.")
        runRequest { auth.signIn(email.trim(), password); navigate(Screen.HOME) }
    }

    fun startGoogleSignIn(openUrl: (String) -> Unit) {
        runRequest { openUrl(auth.googleSignInUrl()) }
    }

    fun handleGoogleCallback(uri: String) {
        runRequest {
            if (auth.acceptGoogleCallback(uri)) navigate(Screen.HOME)
            else showError("Не удалось завершить вход через Google.")
        }
    }

    fun submitStepOne(email: String, password: String, confirmation: String, accepted: Boolean) {
        if (!validEmail(email)) return showError("Введите корректный адрес электронной почты.")
        if (password.isBlank() || confirmation.isBlank()) return showError("Пожалуйста, заполните все обязательные поля.")
        if (password.length < 6) return showError("Пароль должен содержать не менее 6 символов.")
        if (password != confirmation) return showError("Пароли не совпадают.")
        if (!accepted) return showError("Необходимо согласиться с условиями обслуживания и политикой конфиденциальности.")
        registration = registration.copy(email = email.trim(), password = password)
        termsAccepted = accepted
        navigate(Screen.REGISTER_TWO)
    }

    fun saveStepOne(email: String, password: String, accepted: Boolean) {
        registration = registration.copy(email = email, password = password)
        termsAccepted = accepted
        back()
    }

    fun submitStepTwo(surname: String, name: String, patronymic: String, birthDate: String, gender: String) {
        if (surname.isBlank() || name.isBlank() || birthDate.isBlank() || gender.isBlank())
            return showError("Пожалуйста, заполните все обязательные поля.")
        val date = parseDate(birthDate, "MM/dd/uuuu")
        if (date == null || date.isAfter(LocalDate.now())) return showError("Введите корректную дату рождения.")
        registration = registration.copy(surname = surname.trim(), name = name.trim(),
            patronymic = patronymic.trim(), birthDate = birthDate, gender = gender)
        navigate(Screen.REGISTER_THREE)
    }

    fun saveStepTwo(surname: String, name: String, patronymic: String, birthDate: String, gender: String) {
        registration = registration.copy(surname = surname, name = name, patronymic = patronymic,
            birthDate = birthDate, gender = gender)
        back()
    }

    fun setPhoto(kind: PhotoKind, uri: String) {
        registration = when (kind) {
            PhotoKind.AVATAR -> registration.copy(avatarUri = uri)
            PhotoKind.LICENSE -> registration.copy(licenseUri = uri)
            PhotoKind.PASSPORT -> registration.copy(passportUri = uri)
        }
        _state.value = _state.value?.copy(revision = (_state.value?.revision ?: 0) + 1)
    }

    fun submitStepThree(licenseNumber: String, issueDate: String) {
        if (licenseNumber.isBlank() || issueDate.isBlank())
            return showError("Пожалуйста, заполните все обязательные поля.")
        if (licenseNumber.trim().length != 10) return showError("Номер водительского удостоверения должен содержать 10 символов.")
        val date = parseDate(issueDate, "dd/MM/uuuu")
        if (date == null || date.isAfter(LocalDate.now())) return showError("Введите корректную дату выдачи.")
        if (registration.licenseUri == null || registration.passportUri == null)
            return showError("Пожалуйста, загрузите все необходимые фото.")
        registration = registration.copy(licenseNumber = licenseNumber.trim(), issueDate = issueDate)
        runRequest {
            when (auth.register(registration)) {
                AuthOutcome.SIGNED_IN -> navigate(Screen.SUCCESS)
                AuthOutcome.EMAIL_CONFIRMATION_REQUIRED -> {
                    navigate(Screen.AUTH)
                    showError("Подтвердите адрес электронной почты, затем войдите в аккаунт.")
                }
            }
        }
    }

    fun saveStepThree(licenseNumber: String, issueDate: String) {
        registration = registration.copy(licenseNumber = licenseNumber, issueDate = issueDate)
        back()
    }

    fun finishSuccess() = navigate(Screen.HOME)
    fun logout() { session.clear(); navigate(Screen.AUTH) }

    private fun runRequest(action: suspend () -> Unit) {
        _state.value = _state.value?.copy(busy = true)
        viewModelScope.launch {
            try { action() }
            catch (error: Exception) {
                val message = when (error) {
                    is UnknownHostException -> "Не удалось найти сервер. Проверьте интернет и DNS на устройстве."
                    is ConnectException, is SocketTimeoutException -> "Не удалось связаться с сервером. Проверьте интернет и повторите попытку."
                    else -> error.message ?: "Не удалось выполнить запрос."
                }
                showError(message)
            }
            finally { _state.value = _state.value?.copy(busy = false) }
        }
    }

    private fun navigate(screen: Screen) {
        if (_state.value?.screen == Screen.OFFLINE) interruptedScreen = screen
        else _state.value = AppUiState(screen)
    }
    private fun showError(text: String) { _message.value = text }
    private fun validEmail(value: String) = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.(com|ru)$", RegexOption.IGNORE_CASE)
        .matches(value.trim())
    private fun parseDate(value: String, pattern: String): LocalDate? = runCatching {
        LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT))
    }.getOrNull()

    override fun onCleared() { network.stopObserving(); handler.removeCallbacksAndMessages(null) }

    class Factory(
        private val network: NetworkRepository,
        private val onboarding: OnboardingRepository,
        private val session: SessionRepository,
        private val auth: AuthRepository,
        private val checkInternet: CheckInternetUseCase,
        private val resolveDestination: ResolveDestinationUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(network, onboarding, session, auth, checkInternet, resolveDestination) as T
    }
}
