package com.nofar.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppMetadataTest {
    @Test
    fun repositoryAndPrivacyUrls_usePublicHttpsHosts() {
        assertThat(AppMetadata.GITHUB_REPOSITORY_URL).startsWith("https://github.com/")
        assertThat(AppMetadata.PRIVACY_POLICY_URL).isEqualTo("https://yuvallb.github.io/NofAR/privacy/")
    }
}
