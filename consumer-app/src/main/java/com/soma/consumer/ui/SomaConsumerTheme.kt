package com.soma.consumer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SomaBlue = Color(0xFF1E40AF)
private val SomaGreen = Color(0xFF059669)
private val SomaPurple = Color(0xFF7E22CE)
private val SomaOrange = Color(0xFFF97316)

private val ConsumerColorScheme = darkColorScheme(
    primary = SomaBlue,
    secondary = SomaPurple,
    tertiary = SomaOrange
)

@Composable
fun SOMAConsumerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ConsumerColorScheme,
        content = content
    )
}
