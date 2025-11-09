package com.perpheads.files.auth

import io.quarkus.security.identity.request.TokenAuthenticationRequest

class PhFilesApiKeyAuthRequest(credentials: PhFilesApiKeyCredentials) : TokenAuthenticationRequest(credentials)