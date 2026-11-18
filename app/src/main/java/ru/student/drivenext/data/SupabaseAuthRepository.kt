package ru.student.drivenext.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.student.drivenext.BuildConfig
import ru.student.drivenext.domain.AuthOutcome
import ru.student.drivenext.domain.AuthRepository
import ru.student.drivenext.domain.RegistrationData
import ru.student.drivenext.domain.SessionRepository
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SupabaseAuthRepository(
    private val context: Context,
    private val session: SessionRepository
) : AuthRepository {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_KEY
    private val oauthPreferences = context.getSharedPreferences("drivenext_oauth", Context.MODE_PRIVATE)

    override suspend fun signIn(email: String, password: String): AuthOutcome = withContext(Dispatchers.IO) {
        val response = try {
            request(
                "POST", "/auth/v1/token?grant_type=password",
                JSONObject().put("email", email).put("password", password)
            )
        } catch (error: SupabaseRequestException) {
            when {
                error.message.contains("Invalid login credentials", ignoreCase = true) ->
                    throw IllegalArgumentException("Неверный email или пароль.")
                error.message.contains("Email not confirmed", ignoreCase = true) ->
                    throw IllegalStateException("Подтвердите адрес электронной почты, затем войдите.")
                else -> throw error
            }
        }
        saveSession(response)
        AuthOutcome.SIGNED_IN
    }

    override suspend fun register(data: RegistrationData): AuthOutcome = withContext(Dispatchers.IO) {
        verifyRegistrationBackend()
        val metadata = JSONObject()
            .put("surname", data.surname.trim())
            .put("first_name", data.name.trim())
            .put("patronymic", data.patronymic.trim())
        val signup = request(
            "POST", "/auth/v1/signup",
            JSONObject().put("email", data.email.trim()).put("password", data.password)
                .put("data", metadata)
        )
        if (signup.optString("access_token").isBlank()) {
            return@withContext AuthOutcome.EMAIL_CONFIRMATION_REQUIRED
        }
        saveSession(signup)
        val userId = session.userId() ?: error("Не удалось получить ID пользователя.")
        val accessToken = session.accessToken() ?: error("Не удалось получить токен доступа.")
        val avatarPath = data.avatarUri?.let { upload(userId, "avatar", it, accessToken) }
        val licensePath = upload(userId, "license", requireNotNull(data.licenseUri), accessToken)
        val passportPath = upload(userId, "passport", requireNotNull(data.passportUri), accessToken)
        val profile = JSONObject()
            .put("id", userId)
            .put("surname", data.surname.trim())
            .put("first_name", data.name.trim())
            .put("patronymic", data.patronymic.trim().ifBlank { JSONObject.NULL })
            .put("birth_date", LocalDate.parse(data.birthDate, DateTimeFormatter.ofPattern("MM/dd/uuuu")).toString())
            .put("gender", data.gender)
            .put("license_number", data.licenseNumber.trim())
            .put("issue_date", LocalDate.parse(data.issueDate, DateTimeFormatter.ofPattern("dd/MM/uuuu")).toString())
            .put("avatar_path", avatarPath ?: JSONObject.NULL)
            .put("license_photo_path", licensePath)
            .put("passport_photo_path", passportPath)
        request("POST", "/rest/v1/profiles?on_conflict=id", profile, accessToken, mapOf("Prefer" to "resolution=merge-duplicates"))
        AuthOutcome.SIGNED_IN
    }

    override suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = session.refreshToken() ?: return@withContext false
        try {
            saveSession(request(
                "POST", "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refreshToken)
            ))
            true
        } catch (error: SupabaseRequestException) {
            if (error.code in 400..499) session.clear()
            false
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun googleSignInUrl(): String = withContext(Dispatchers.IO) {
        requireConfigured()
        val settings = request("GET", "/auth/v1/settings")
        check(settings.optJSONObject("external")?.optBoolean("google") == true) {
            "Вход через Google не включён в настройках Supabase."
        }
        oauthPreferences.edit().putLong("pending_at", System.currentTimeMillis()).apply()
        Uri.parse("$baseUrl/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", "drivenext://auth-callback")
            .build().toString()
    }

    override suspend fun acceptGoogleCallback(uri: String): Boolean = withContext(Dispatchers.IO) {
        val callback = Uri.parse(uri)
        val startedAt = oauthPreferences.getLong("pending_at", 0L)
        oauthPreferences.edit().remove("pending_at").apply()
        if (callback.scheme != "drivenext" || callback.host != "auth-callback" ||
            startedAt == 0L || System.currentTimeMillis() - startedAt > 5 * 60 * 1000L) return@withContext false
        val params = Uri.parse("https://callback.invalid/?${callback.fragment.orEmpty()}")
        val error = params.getQueryParameter("error_description")
            ?: callback.getQueryParameter("error_description")
        if (error != null) throw IllegalStateException(error)
        val accessToken = params.getQueryParameter("access_token") ?: return@withContext false
        val refreshToken = params.getQueryParameter("refresh_token") ?: return@withContext false
        val expiresIn = params.getQueryParameter("expires_in")?.toLongOrNull() ?: 3600L
        val user = request("GET", "/auth/v1/user", null, accessToken)
        val userId = user.optString("id").takeIf { it.isNotBlank() } ?: return@withContext false
        session.save(accessToken, refreshToken, userId, expiresIn)
        true
    }

    private fun saveSession(response: JSONObject) {
        val accessToken = response.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Сервер не вернул токен доступа.")
        val refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: error("Сервер не вернул токен обновления.")
        val userId = response.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: error("Сервер не вернул ID пользователя.")
        session.save(accessToken, refreshToken, userId, response.optLong("expires_in", 3600L))
    }

    private fun verifyRegistrationBackend() {
        val missing = mutableListOf<String>()
        try {
            requestBytes("GET", "/rest/v1/profiles?select=id&limit=1", null,
                "application/json", null, emptyMap())
        } catch (error: SupabaseRequestException) {
            if (error.code == 404) missing += "таблица profiles"
            else if (error.code !in listOf(401, 403)) throw error
        }
        val settings = request("GET", "/auth/v1/settings")
        if (!settings.optBoolean("mailer_autoconfirm")) missing += "подтверждение email включено"
        if (missing.isNotEmpty()) {
            error("Сервер регистрации ещё не настроен: ${missing.joinToString()}. Выполните настройку Supabase из README.")
        }
    }

    private fun upload(userId: String, name: String, uriString: String, token: String): String {
        val uri = Uri.parse(uriString)
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть выбранное фото.")
        val bytes = stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 10 * 1024 * 1024) error("Размер фото не должен превышать 10 МБ.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "$userId/$name.$extension"
        requestBytes("POST", "/storage/v1/object/identity-documents/$path", bytes, mime, token,
            mapOf("x-upsert" to "true"))
        return path
    }

    private fun request(
        method: String, path: String, body: JSONObject? = null,
        token: String? = null, headers: Map<String, String> = emptyMap()
    ): JSONObject {
        val response = requestBytes(method, path, body?.toString()?.toByteArray(Charsets.UTF_8),
            "application/json", token, headers)
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun requestBytes(
        method: String, path: String, body: ByteArray?, mime: String,
        token: String?, headers: Map<String, String>
    ): String {
        requireConfigured()
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", mime)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    val json = JSONObject(text)
                    json.optString("msg").ifBlank {
                        json.optString("message").ifBlank { json.optString("error_description") }
                    }
                }.getOrNull().orEmpty().ifBlank { "Ошибка сервера ($code)." }
                throw SupabaseRequestException(code, message)
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun requireConfigured() {
        check(baseUrl.startsWith("https://") && key.isNotBlank()) {
            "Укажите URL проекта и Publishable key Supabase в local.properties."
        }
    }
}

class SupabaseRequestException(val code: Int, override val message: String) : Exception(message)
