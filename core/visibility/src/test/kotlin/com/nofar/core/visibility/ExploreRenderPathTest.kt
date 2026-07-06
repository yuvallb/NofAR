package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Modifier
import org.junit.Test

class ExploreRenderPathTest {
    @Test
    fun renderPathTypes_arePureSynchronousFunctions() {
        assertNoContinuationParameters(ScreenProjector::class.java)
        assertNoContinuationParameters(LabelClustering::class.java)
        assertNoContinuationParameters(ExploreLabelRenderer::class.java)
        assertNoContinuationParameters(ExploreDistanceFormatter::class.java)
    }

    @Test
    fun renderPathTypes_areNotCoroutineScopeTypes() {
        assertThat(Modifier.isInterface(ScreenProjector::class.java.modifiers)).isFalse()
    }

    private fun assertNoContinuationParameters(clazz: Class<*>) {
        val continuationMethods =
            clazz.declaredMethods.filter { method ->
                method.parameterTypes.any { parameter -> parameter.name.contains("Continuation") }
            }
        assertThat(continuationMethods).isEmpty()
    }
}
