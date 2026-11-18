package ru.student.drivenext.data

import android.content.Context
import ru.student.drivenext.domain.OnboardingRepository
import ru.student.drivenext.domain.SessionRepository

class PreferencesOnboardingRepository(context: Context) : OnboardingRepository {
    private val preferences = context.getSharedPreferences("drivenext", Context.MODE_PRIVATE)

    override fun isCompleted(): Boolean = preferences.getBoolean("onboarding_completed", false)

    override fun complete() {
        preferences.edit().putBoolean("onboarding_completed", true).apply()
    }
}

class PreferencesSessionRepository(context: Context) : SessionRepository {
    private val preferences = context.getSharedPreferences("drivenext", Context.MODE_PRIVATE)

    override fun hasValidAccessToken(): Boolean {
        val token = preferences.getString("access_token", null)
        val expiresAt = preferences.getLong("access_token_expires_at", 0L)
        return !token.isNullOrBlank() && expiresAt > System.currentTimeMillis() + 30_000L
    }

    override fun hasRefreshToken() = !preferences.getString("refresh_token", null).isNullOrBlank()
    override fun accessToken(): String? = preferences.getString("access_token", null)
    override fun refreshToken(): String? = preferences.getString("refresh_token", null)
    override fun userId(): String? = preferences.getString("user_id", null)

    override fun save(accessToken: String, refreshToken: String, userId: String, expiresInSeconds: Long) {
        preferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .putLong("access_token_expires_at", System.currentTimeMillis() + expiresInSeconds * 1000L)
            .apply()
    }

    override fun clear() {
        preferences.edit().remove("access_token").remove("refresh_token")
            .remove("user_id").remove("access_token_expires_at").apply()
    }
}
