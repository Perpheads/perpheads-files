package com.perpheads.files.auth

import io.quarkus.security.credential.TokenCredential
import io.quarkus.security.identity.request.TokenAuthenticationRequest

class PhFilesCookieAuthRequest(private val cookieCredential: PhFilesCookieCredential) : TokenAuthenticationRequest(cookieCredential) {
    override fun getToken(): TokenCredential = cookieCredential
}