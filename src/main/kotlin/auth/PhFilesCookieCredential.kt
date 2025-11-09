package com.perpheads.files.auth

import io.quarkus.security.credential.TokenCredential

class PhFilesCookieCredential(cookieId: String): TokenCredential(cookieId, "cookie")