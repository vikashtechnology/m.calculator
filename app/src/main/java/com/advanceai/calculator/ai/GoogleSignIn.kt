package com.advanceai.calculator.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.advanceai.calculator.BuildConfig

/**
 * "Continue with Google" for the app itself.
 *
 * The ID token Google returns identifies the same Google account the user can
 * pick inside Puter's own sign-in window, so one Google account covers both.
 *
 * The button stays hidden until an OAuth *Web* client ID is configured (see
 * gradle.properties and app/signing/fingerprints.txt), because a client ID that
 * does not match the signing certificate would only produce errors.
 */
object GoogleSignIn {

    private const val Tag = "GoogleSignIn"
    private const val PrefsName = "m_calculator_google"
    private const val KeyToken = "id_token"
    private const val KeyEmail = "email"
    private const val KeyIssuedAt = "issued_at"

    /** Google ID tokens last an hour; ask for a fresh one before that. */
    private const val TokenLifetimeMs = 55 * 60 * 1000L

    /** Web OAuth client ID, or an empty string when Google Sign-In is off. */
    val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val isConfigured: Boolean
        get() = webClientId.isNotBlank()

    /** Outcome of a sign-in attempt, as plain strings for the WebView bridge. */
    data class Result(
        val ok: Boolean,
        val idToken: String = "",
        val email: String = "",
        val name: String = "",
        val error: String = ""
    )

    /**
     * Shows the Google account picker.
     *
     * [context] should be an Activity (or a wrapper around one) so the picker
     * has a window to appear in.
     */
    suspend fun signIn(context: Context): Result = when {
        !isConfigured -> Result(ok = false, error = "not_configured")
        else -> requestCredential(context)
    }

    /** Forgets the account so the next tap shows the account picker again. */
    suspend fun signOut(context: Context) {
        forget(context)
        if (!isConfigured) return
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(Tag, "clearCredentialState failed", e)
        }
    }

    /**
     * The token handed to the stats Worker for Ask AI, or null when the user has
     * not signed in (or the token is old enough that Google would refuse it).
     */
    fun cachedToken(context: Context): String? {
        val sp = prefs(context)
        val token = sp.getString(KeyToken, null)
        if (token.isNullOrBlank()) return null
        val age = System.currentTimeMillis() - sp.getLong(KeyIssuedAt, 0L)
        return if (age in 0 until TokenLifetimeMs) token else null
    }

    /** The signed-in Google address, or an empty string. */
    fun cachedEmail(context: Context): String =
        prefs(context).getString(KeyEmail, "").orEmpty()

    fun isSignedIn(context: Context): Boolean = cachedToken(context) != null

    private fun remember(context: Context, token: String, email: String) {
        prefs(context).edit()
            .putString(KeyToken, token)
            .putString(KeyEmail, email)
            .putLong(KeyIssuedAt, System.currentTimeMillis())
            .apply()
    }

    private fun forget(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    private suspend fun requestCredential(context: Context): Result {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            // The account is only used to sign in to the AI service, so the
            // picker may list every Google account on the device.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = CredentialManager.create(context)
                .getCredential(context = context, request = request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data)
                remember(context, token.idToken, token.id)
                Result(
                    ok = true,
                    idToken = token.idToken,
                    email = token.id,
                    name = token.displayName.orEmpty()
                )
            } else {
                Result(ok = false, error = "unexpected_credential")
            }
        } catch (e: GetCredentialCancellationException) {
            Result(ok = false, error = "cancelled")
        } catch (e: NoCredentialException) {
            Result(ok = false, error = "no_google_account")
        } catch (e: Exception) {
            Log.w(Tag, "Google sign-in failed", e)
            Result(ok = false, error = e.message ?: "sign_in_failed")
        }
    }

    /** Unwraps a Compose context into the Activity hosting the UI. */
    fun activityOf(context: Context): Context {
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return context.applicationContext
    }
}
