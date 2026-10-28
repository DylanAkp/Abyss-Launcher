package dev.dylanakp.abysstvlauncher.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

private object NoIndication : IndicationNodeFactory {
    private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
        }
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

@Composable
fun AbyssTVLauncherTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFFF5F5F7),       // Abyss near-white accent (--abyss-accent)
        secondary = Color(0xFF8E9BAE),
        tertiary = AbyssGold,
        background = AbyssVoid,
        surface = AbyssObsidian,
        surfaceVariant = AbyssSurfaceGlass,
        onPrimary = Color(0xFF121212),
        onSecondary = AbyssTextPrimary,
        onBackground = AbyssTextPrimary,
        onSurface = AbyssTextPrimary,
        onSurfaceVariant = AbyssTextSecondary,
        outline = AbyssBorderHairline
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AbyssTypography
    ) {
        CompositionLocalProvider(LocalIndication provides NoIndication) {
            content()
        }
    }
}
