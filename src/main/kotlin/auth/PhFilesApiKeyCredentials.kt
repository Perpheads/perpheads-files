package com.perpheads.files.auth

import io.quarkus.security.credential.TokenCredential

class PhFilesApiKeyCredentials(apiKey: String) : TokenCredential(apiKey, "api_key")