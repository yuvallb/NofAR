package com.nofar.core.model

/**
 * Application metadata shared across modules. Keep in sync with :app `defaultConfig`.
 */
object AppMetadata {
    const val APP_NAME: String = "NofAR"
    const val TAGLINE: String = "point, explore, discover"
    const val VERSION_NAME: String = "1.0.0"
    const val VERSION_CODE: Int = 1

    /** Public git repository (issues, source, license). */
    const val GITHUB_REPOSITORY_URL: String = "https://github.com/yuvallb/NofAR"

    /**
     * Hosted privacy policy (GitHub Pages from `/docs`).
     * Enable Pages: Settings → Pages → Deploy from branch `main` / folder `/docs`.
     */
    const val PRIVACY_POLICY_URL: String = "https://yuvallb.github.io/NofAR/privacy/"
}
