package com.helper.app.data.update

/**
 * Конфигурация self-update: репозиторий GitHub Releases.
 *
 * Репо public → публичные Releases качаются без авторизации,
 * токен в приложение не нужен.
 */
object UpdateConfig {
    const val OWNER = "Chegolin123"
    const val REPO = "helper-android"

    /** GitHub API: последний релиз. */
    const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** Имя ассета с APK внутри релиза (фиксированное). */
    const val APK_ASSET_NAME = "helper.apk"
}
