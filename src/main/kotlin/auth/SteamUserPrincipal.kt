package com.perpheads.files.auth

import com.perpheads.files.data.AccountInfoResponse
import java.security.Principal

class SteamUserPrincipal(val account: AccountInfoResponse) : Principal {
    override fun getName(): String = "SteamUser(${account.name}): ${account.communityId}"
}