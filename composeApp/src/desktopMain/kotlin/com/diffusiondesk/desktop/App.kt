package com.diffusiondesk.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diffusiondesk.desktop.theme.DiffusionDeskTheme

private enum class Screen(val label: String, val icon: ImageVector, val subtitle: String) {
    Generate("Generate", Icons.Default.Image, "Text-to-image, img2img, and upscale flows will land here first."),
    Gallery("Gallery", Icons.Default.Collections, "Saved outputs, filters, tags, ratings, and reuse actions."),
    Manager("Manager", Icons.Default.Tune, "Presets, styles, LoRAs, and model metadata."),
    Inpaint("Inpaint", Icons.Default.Draw, "Canvas-based editing and mask export will live here."),
    Explore("Explore", Icons.Default.AutoAwesome, "Mutation grid and prompt exploration."),
    Assistant("Assistant", Icons.Default.SmartToy, "Chat, tool calls, and multimodal assist."),
    Settings("Settings", Icons.Default.Settings, "Desktop preferences and backend bootstrap settings."),
}

@Composable
fun App(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf(Screen.Generate) }

    DiffusionDeskTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationSidebar(
                    currentScreen = currentScreen,
                    darkTheme = darkTheme,
                    onSelect = { currentScreen = it },
                    onToggleTheme = onToggleTheme,
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    Header(currentScreen = currentScreen)
                    ScreenPlaceholder(screen = currentScreen)
                }
            }
        }
    }
}

@Composable
private fun NavigationSidebar(
    currentScreen: Screen,
    darkTheme: Boolean,
    onSelect: (Screen) -> Unit,
    onToggleTheme: () -> Unit,
) {
    NavigationRail(
        modifier = Modifier.width(88.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))

        Screen.entries.forEach { screen ->
            NavigationRailItem(
                selected = currentScreen == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label, fontSize = 10.sp) },
                alwaysShowLabel = true,
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle theme",
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Header(currentScreen: Screen) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = currentScreen.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Compose desktop skeleton. Backend wiring comes next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScreenPlaceholder(screen: Screen) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = screen.label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = screen.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusCard(
                    title = "Current Status",
                    body = "This is the first native shell. It establishes the Compose app structure, future navigation, and theming without backend coupling yet.",
                )
                StatusCard(
                    title = "Next Step",
                    body = when (screen) {
                        Screen.Generate -> "Wire the orchestrator process manager, HTTP client, and generation state first."
                        Screen.Gallery -> "Add typed DTOs and a lazy grid backed by the existing /v1/history/images API."
                        Screen.Manager -> "Port presets and model metadata workflows after generation is stable."
                        Screen.Inpaint -> "Add a custom Compose Canvas editor once core backend connectivity is in place."
                        Screen.Explore -> "Port mutation-builder logic after generation requests are wired."
                        Screen.Assistant -> "Add chat state, markdown rendering, and tool-call visualization after backend bootstrap."
                        Screen.Settings -> "Persist desktop settings and add file pickers for model/output directories."
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}