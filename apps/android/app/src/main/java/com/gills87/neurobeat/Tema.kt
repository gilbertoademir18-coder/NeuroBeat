package com.gills87.neurobeat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * O violeta da marca (o degradê do ícone), com os mesmos tons do web
 * (`apps/web/src/app.css`). Fixo, e não o "Material You": as cores dinâmicas
 * vêm do papel de parede e deixavam o app rosa.
 */

private val Claro = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDD6FE),
    onSecondaryContainer = Color(0xFF2E1065),
    background = Color(0xFFF8F7FB),
    onBackground = Color(0xFF1D1A24),
    surface = Color(0xFFF8F7FB),
    onSurface = Color(0xFF1D1A24),
    surfaceVariant = Color(0xFFE6E3EE),
    onSurfaceVariant = Color(0xFF5B5568),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2FA),
    surfaceContainer = Color(0xFFEFECF7),
    surfaceContainerHigh = Color(0xFFE9E5F4),
    surfaceContainerHighest = Color(0xFFE3DEF0),
    outline = Color(0xFF8E879C),
    outlineVariant = Color(0xFFE6E3EE),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF170F2B),
    primaryContainer = Color(0xFF2B2245),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF170F2B),
    secondaryContainer = Color(0xFF3B2D63),
    onSecondaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF110F17),
    onBackground = Color(0xFFEBE8F1),
    surface = Color(0xFF110F17),
    onSurface = Color(0xFFEBE8F1),
    surfaceVariant = Color(0xFF2B2735),
    onSurfaceVariant = Color(0xFFB9B3C7),
    surfaceContainerLowest = Color(0xFF0C0A11),
    surfaceContainerLow = Color(0xFF15121C),
    surfaceContainer = Color(0xFF19161F),
    surfaceContainerHigh = Color(0xFF221D2C),
    surfaceContainerHighest = Color(0xFF2B2538),
    outline = Color(0xFF6E6680),
    outlineVariant = Color(0xFF2B2735),
    error = Color(0xFFF97066),
    onError = Color(0xFF2B0A07),
)

@Composable
fun NeuroBeatTema(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Escuro else Claro, content = conteudo)
}
