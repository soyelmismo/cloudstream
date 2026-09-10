package com.lagradost.cloudstream3.utils

/**
 * Simple helper to get the short commit hash from assets / resources.
 * The hash is generated at build and stored as an asset or resource
 * that can be accessed at runtime for Gradle
 * configuration cache support.
 */
expect object GitInfo {
    fun currentCommitHash(): String
}
