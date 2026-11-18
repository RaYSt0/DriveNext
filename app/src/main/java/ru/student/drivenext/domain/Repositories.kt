package ru.student.drivenext.domain

interface NetworkRepository {
    fun isOnline(): Boolean
    fun observe(onChanged: (Boolean) -> Unit)
    fun stopObserving()
}

interface OnboardingRepository {
    fun isCompleted(): Boolean
    fun complete()
}

interface SessionRepository {
    fun hasValidAccessToken(): Boolean
    fun hasRefreshToken(): Boolean
    fun accessToken(): String?
    fun refreshToken(): String?
    fun userId(): String?
    fun save(accessToken: String, refreshToken: String, userId: String, expiresInSeconds: Long)
    fun clear()
}

data class RegistrationData(
    val email: String = "",
    val password: String = "",
    val surname: String = "",
    val name: String = "",
    val patronymic: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val licenseNumber: String = "",
    val issueDate: String = "",
    val avatarUri: String? = null,
    val licenseUri: String? = null,
    val passportUri: String? = null
)

enum class AuthOutcome { SIGNED_IN, EMAIL_CONFIRMATION_REQUIRED }

interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthOutcome
    suspend fun register(data: RegistrationData): AuthOutcome
    suspend fun refreshSession(): Boolean
    suspend fun googleSignInUrl(): String
    suspend fun acceptGoogleCallback(uri: String): Boolean
}

class CheckInternetUseCase(private val network: NetworkRepository) {
    operator fun invoke(): Boolean = network.isOnline()
}

enum class Destination { ONBOARDING, AUTH, HOME }

class ResolveDestinationUseCase(
    private val onboarding: OnboardingRepository,
    private val session: SessionRepository
) {
    operator fun invoke(): Destination = when {
        session.hasValidAccessToken() -> Destination.HOME
        !onboarding.isCompleted() -> Destination.ONBOARDING
        else -> Destination.AUTH
    }
}
