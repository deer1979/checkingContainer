package com.checkingcontainer.core.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Gestiona la autenticación OAuth2 con Service Account de Google.
 *
 * ## Flujo
 * 1. Construye un JWT firmado con RS256 usando la clave PKCS8 del Service Account.
 * 2. Intercambia el JWT por un Bearer token en [ServiceAccountCredentials.tokenUri].
 * 3. Cachea el token; lo refresca automáticamente cuando quedan menos de 60 s de vigencia.
 *
 * ## Thread-safety
 * [getAccessToken] es `suspend` y corre en [Dispatchers.IO].
 * El token cacheado se actualiza con `@Volatile`; en escenarios de alta concurrencia
 * puede emitirse más de un refresh simultáneo, lo que es inofensivo (el último gana).
 *
 * ⚠️ SEGURIDAD: No exponer ni loguear el token ni los campos de las credenciales.
 */
class GoogleAuthManager(
    private val credentials: ServiceAccountCredentials,
    private val httpClient: OkHttpClient,
) {

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in")   val expiresIn: Int = 3600,
        @SerialName("token_type")   val tokenType: String = "Bearer",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Scopes requeridos: lectura/escritura de hojas. */
    private val SCOPE = "https://www.googleapis.com/auth/spreadsheets"

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiry: Long = 0L   // epoch ms

    /**
     * Devuelve un Bearer token válido.
     * Lo refresca si faltan menos de 60 s para que expire.
     */
    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { now < tokenExpiry - 60_000L } ?: refreshToken()
    }

    // ── Token refresh ────────────────────────────────────────────────────────

    private fun refreshToken(): String {
        val jwt = buildJwt()
        val formBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()

        val request = Request.Builder()
            .url(credentials.tokenUri)
            .post(formBody)
            .build()

        val responseBody = httpClient.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) {
                "Token exchange failed [${resp.code}]: ${resp.body?.string()}"
            }
            resp.body!!.string()
        }

        val tokenResp = json.decodeFromString<TokenResponse>(responseBody)
        cachedToken  = tokenResp.accessToken
        tokenExpiry  = System.currentTimeMillis() + tokenResp.expiresIn * 1_000L
        Log.d(TAG, "Token refreshed. Expires in ${tokenResp.expiresIn}s")
        return tokenResp.accessToken
    }

    // ── JWT RS256 ────────────────────────────────────────────────────────────

    private fun buildJwt(): String {
        val nowSec = System.currentTimeMillis() / 1_000L
        val header  = b64url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = b64url(
            """{"iss":"${credentials.clientEmail}",""" +
            """"scope":"$SCOPE",""" +
            """"aud":"${credentials.tokenUri}",""" +
            """"iat":$nowSec,""" +
            """"exp":${nowSec + 3_600}}"""
        )
        val signingInput = "$header.$payload"
        val signature    = rsaSign(signingInput, credentials.privateKey)
        return "$signingInput.$signature"
    }

    /**
     * Firma [input] con la clave privada RSA PKCS8 en formato PEM.
     * Usa `SHA256withRSA` (RS256) tal como requiere Google OAuth2.
     */
    private fun rsaSign(input: String, pemPrivateKey: String): String {
        val stripped = pemPrivateKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        val keyBytes  = Base64.decode(stripped, Base64.DEFAULT)
        val keySpec   = PKCS8EncodedKeySpec(keyBytes)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

        return Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(input.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(sign(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }

    /** Base64url-encodes a UTF-8 string (no padding, URL-safe charset). */
    private fun b64url(text: String): String =
        Base64.encodeToString(
            text.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

    private companion object {
        const val TAG = "GoogleAuthManager"
    }
}
