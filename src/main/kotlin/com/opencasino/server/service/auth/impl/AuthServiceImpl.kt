package com.opencasino.server.service.auth.impl

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.opencasino.server.config.OAuth2Properties
import com.opencasino.server.event.AuthEvent
import com.opencasino.server.service.auth.AuthService
import org.springframework.stereotype.Service
import java.util.Collections

@Service
class AuthServiceImpl(
    private val oAuth2Properties: OAuth2Properties
) : AuthService {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    private val verifier = GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
        .setAudience(Collections.singletonList(oAuth2Properties.clientId))
        .build()

   override fun authenticate(authString: String): AuthEvent {
        val tokenResponse = GoogleAuthorizationCodeTokenRequest(
            httpTransport, jsonFactory,
            oAuth2Properties.clientId, oAuth2Properties.clientSecret,
            authString, oAuth2Properties.redirectUri[0]
        ).execute()

        val idToken = verifier.verify(tokenResponse.idToken) ?: throw Exception("Invalid token")

        val payload = idToken.payload
        val userID = payload.subject

        //Create an auth event on success
        return AuthEvent(userID, payload.email)
    }
}