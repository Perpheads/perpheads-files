package com.perpheads.files.utils

import java.util.Random


fun Long.profileIdToSteamId(): String? {
    val steamId1 = this % 2
    val steamId2 = this - 76561197960265728L
    if (steamId2 <= 0) return null
    val steamId3 = (steamId2 - steamId1) / 2
    return "STEAM_0:$steamId1:$steamId3"
}

fun String.profileIdToSteamId(): String? {
    val communityId = this.substring(this.lastIndexOf("/") + 1).toLong()
    return communityId.profileIdToSteamId()
}

fun String.convertToSteamId(): String? {
    if (this.contains("/")) {
        return substring(lastIndexOf("/")).convertToSteamId()
    }
    return if (startsWith("STEAM_")) {
        this
    } else if (length == 17 || this == "0") {
        this.toLong().profileIdToSteamId()
    } else {
        null
    }
}

fun Random.alphaNumeric(len: Int): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..len).map {
        chars[nextInt(chars.length)]
    }.joinToString("")
}

fun String.convertToProfileId(): Long? {
    if (this.contains("/")) {
        return substring(lastIndexOf("/")).convertToProfileId()
    }
    return if (startsWith("STEAM_")) {
        this.steamIdToProfileId()
    } else if (length == 17 || this == "0") {
        this.toLong()
    } else {
        null
    }
}

private val steamIdRegex = Regex("^STEAM_[0-1]:[0-1]:[0-9]+$")
private val otherSteamIdRegex = Regex("^\\[U:[0-1]:[0-9]+]+$")

fun String.steamIdToProfileId(): Long? {
    if (this == "STEAM_ID_LAN" || this == "BOT") return null
    return if (this.matches(steamIdRegex)) {
        val tmpId = this.substring(8).split(":")
        runCatching {
            tmpId[0].toLong() + tmpId[1].toLong() * 2 + 76561197960265728L
        }.getOrNull()
    } else if (this.matches(otherSteamIdRegex)) {
        val tmpId = this.substring(3, this.length - 1).split(":")
        runCatching {
            tmpId[0].toLong() + tmpId[1].toLong() + 76561197960265728L
        }.getOrNull()

    } else null
}

fun String.pluralize(number: Number): String {
    return if (number == 0 || number == 1) {
        this
    } else {
        this.plus("s")
    }
}