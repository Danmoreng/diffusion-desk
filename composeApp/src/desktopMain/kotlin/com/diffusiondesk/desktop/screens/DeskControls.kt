package com.diffusiondesk.desktop.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.OutlinedSlimButton
import org.jetbrains.jewel.ui.component.Text
import java.util.Locale
import kotlin.math.roundToInt

internal val DeskLayoutGap = 6.dp
internal val DeskScreenPadding = DeskLayoutGap
internal val DeskPanelPadding = 12.dp
internal val DeskPanelSpacing = DeskLayoutGap
internal val DeskSectionSpacing = DeskLayoutGap
internal val DeskControlSpacing = DeskLayoutGap
internal val DeskCompactControlSpacing = 4.dp
internal val DeskGroupSpacing = DeskLayoutGap
internal val DeskTabSpacing = DeskLayoutGap
internal val DeskTabHorizontalPadding = 10.dp
internal val DeskIconSize = 16.dp
internal val DeskCompactControlHeight = 36.dp
internal val DeskPanelCornerRadius = 10.dp
internal val DeskControlCornerRadius = 6.dp
internal const val DeskSubtleSurfaceAlpha = 0.52f
internal const val DeskSelectedContainerAlpha = 0.16f
internal const val DeskStatusContainerAlpha = 0.14f
internal const val DeskStatusBorderAlpha = 0.45f

internal enum class DeskStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
}

internal data class DeskTabItem(
    val selected: Boolean,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
internal fun deskStatusColor(tone: DeskStatusTone): Color {
    return when (tone) {
        DeskStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        DeskStatusTone.Info -> MaterialTheme.colorScheme.primary
        DeskStatusTone.Success -> Color(0xFF6AAB73)
        DeskStatusTone.Warning -> Color(0xFFCF8E6D)
        DeskStatusTone.Error -> MaterialTheme.colorScheme.error
    }
}

@Composable
internal fun DeskButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    DefaultButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
internal fun DeskOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    slim: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (slim) {
        OutlinedSlimButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}

@Composable
internal fun DeskIsland(
    modifier: Modifier = Modifier,
    padding: Dp = DeskPanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(DeskPanelCornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
        content = content,
    )
}

@Composable
internal fun DeskPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DeskIsland(
        modifier = modifier,
        content = content,
    )
}

@Composable
internal fun DeskSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    DeskPanel(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
internal fun DeskStatusDot(
    tone: DeskStatusTone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(9.dp)
            .background(deskStatusColor(tone), CircleShape),
    )
}

@Composable
internal fun DeskStatusBadge(
    text: String,
    tone: DeskStatusTone,
    modifier: Modifier = Modifier,
) {
    val color = deskStatusColor(tone)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = DeskStatusContainerAlpha))
            .border(1.dp, color.copy(alpha = DeskStatusBorderAlpha), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DeskSummaryTile(
    label: String,
    value: String,
    tone: DeskStatusTone,
    modifier: Modifier = Modifier,
) {
    val color = deskStatusColor(tone)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
            .padding(DeskLayoutGap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
internal fun DeskChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tone: DeskStatusTone = if (selected) DeskStatusTone.Info else DeskStatusTone.Neutral,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else deskStatusColor(tone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = DeskSelectedContainerAlpha)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(999.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = if (onRemove == null) 10.dp else 6.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $text",
                tint = color,
                modifier = Modifier
                    .size(15.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
internal fun DeskNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = DeskSelectedContainerAlpha) else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .width(58.dp)
            .clip(RoundedCornerShape(DeskPanelCornerRadius))
            .background(selectedColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DeskTabHeader(
    tabs: List<DeskTabItem>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = DeskPanelPadding,
    verticalPadding: Dp = DeskControlSpacing,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(DeskPanelCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(DeskTabSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            DeskTabButton(tab)
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
private fun DeskTabButton(tab: DeskTabItem) {
    val container = if (tab.selected) MaterialTheme.colorScheme.primary.copy(alpha = DeskSelectedContainerAlpha) else Color.Transparent
    val content = if (tab.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(container)
            .clickable(onClick = tab.onClick)
            .padding(horizontal = DeskTabHorizontalPadding, vertical = DeskCompactControlSpacing),
        horizontalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(DeskIconSize))
        Text(tab.label, color = content, fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
internal fun DeskTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label.isNotBlank()) {
            DeskLabel(label)
        }
        DeskInputFrame(minHeight = if (singleLine) 34.dp else 70.dp) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = if (singleLine) 0.dp else 7.dp),
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                    ) {
                        if (value.isEmpty() && placeholder.isNotBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
internal fun DeskDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label.isNotBlank()) {
            DeskLabel(label)
        }
        Box(
            modifier = Modifier.onGloballyPositioned { anchorSize = it.size },
        ) {
            DeskInputFrame(
                modifier = Modifier.clickable { expanded = true },
                minHeight = 34.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DeskDropdownPopup(
                expanded = expanded,
                anchorSize = anchorSize,
                options = options,
                onDismissRequest = { expanded = false },
                onSelect = {
                    onValueChange(it)
                    expanded = false
                },
            )
        }
    }
}

@Composable
internal fun DeskCompactDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minMenuWidth: Dp = 160.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier.onGloballyPositioned { anchorSize = it.size },
    ) {
        DeskInlineInputFrame(
            label = label,
            modifier = Modifier.clickable(enabled = options.isNotEmpty()) { expanded = true },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DeskAnchoredDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            options = options,
            anchorSize = anchorSize,
            minWidth = minMenuWidth,
            onSelect = { option ->
                onValueChange(option)
                expanded = false
            },
        )
    }
}

@Composable
internal fun DeskCompactTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeskInlineInputFrame(
        label = label,
        modifier = modifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
internal fun DeskCompactNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    step: Double,
    minValue: Double? = null,
    maxValue: Double? = null,
    decimalPlaces: Int = 0,
) {
    DeskInlineInputFrame(
        label = label,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 30.dp)
                    .padding(end = 3.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
            DeskNumberStepper(
                onIncrement = {
                    onValueChange(stepNumberValue(value, step, minValue, maxValue, decimalPlaces))
                },
                onDecrement = {
                    onValueChange(stepNumberValue(value, -step, minValue, maxValue, decimalPlaces))
                },
            )
        }
    }
}

@Composable
private fun DeskNumberStepper(
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(20.dp)
            .fillMaxHeight(),
    ) {
        DeskNumberStepperButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Increase value",
            onClick = onIncrement,
            modifier = Modifier.weight(1f),
        )
        DeskNumberStepperButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Decrease value",
            onClick = onDecrement,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DeskNumberStepperButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun stepNumberValue(
    value: String,
    delta: Double,
    minValue: Double?,
    maxValue: Double?,
    decimalPlaces: Int,
): String {
    val current = value.toDoubleOrNull() ?: 0.0
    val stepped = (current + delta)
        .let { if (minValue == null) it else it.coerceAtLeast(minValue) }
        .let { if (maxValue == null) it else it.coerceAtMost(maxValue) }

    return if (decimalPlaces > 0) {
        "%.${decimalPlaces}f".format(Locale.US, stepped)
    } else {
        stepped.roundToInt().toString()
    }
}

@Composable
internal fun DeskSearchableTextDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onOptionSelected: ((String) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val query = value.trim()
    val filteredOptions = remember(options, query) {
        val normalizedQuery = query.lowercase()
        options
            .filter { option -> normalizedQuery.isBlank() || option.lowercase().contains(normalizedQuery) }
            .take(10)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label.isNotBlank()) {
            DeskLabel(label)
        }
        Box(
            modifier = Modifier.onGloballyPositioned { anchorSize = it.size },
        ) {
            DeskInputFrame(minHeight = 34.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 9.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            onValueChange(it)
                            expanded = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) expanded = true
                            },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (value.isEmpty() && placeholder.isNotBlank()) {
                                    Text(
                                        text = placeholder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { expanded = true },
                    )
                }
            }
            DeskDropdownPopup(
                expanded = focused && expanded && filteredOptions.isNotEmpty(),
                anchorSize = anchorSize,
                options = filteredOptions,
                focusable = false,
                onDismissRequest = { expanded = false },
                onSelect = {
                    (onOptionSelected ?: onValueChange)(it)
                    expanded = false
                },
            )
        }
    }
}

@Composable
internal fun DeskIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    loading: Boolean = false,
    tooltip: String = contentDescription,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    DeskTooltip(text = tooltip) {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(32.dp)
                .clip(RoundedCornerShape(5.dp)),
            enabled = enabled && !loading,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
internal fun DeskCompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(5.dp)
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .size(width = DeskCompactControlHeight, height = DeskCompactControlHeight)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.45f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun DeskMiniIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier
            .size(width = 28.dp, height = 24.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.75f else 0.35f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
internal fun DeskSubtleTextButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

@Composable
internal fun DeskProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    animationLabel: String = "desk-progress",
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = animationLabel,
    )
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun DeskTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    if (text.isBlank()) {
        content()
        return
    }
    var hovering by remember { mutableStateOf(false) }
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    Box(
        modifier = Modifier.pointerMoveFilter(
            onEnter = {
                hovering = true
                false
            },
            onExit = {
                hovering = false
                false
            },
        ),
    ) {
        content()
        if (hovering) {
            Popup(
                popupPositionProvider = DeskDropdownPositionProvider(gapPx),
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeskLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DeskInputFrame(
    modifier: Modifier = Modifier,
    minHeight: Dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
internal fun DeskInlineInputFrame(
    label: String,
    modifier: Modifier = Modifier,
    labelMinWidth: Dp = 50.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(DeskControlCornerRadius)
    Row(
        modifier = modifier
            .height(DeskCompactControlHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = labelMinWidth)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 9.dp, end = 9.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 9.dp, end = 7.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    }
}

@Composable
private fun DeskDropdownPopup(
    expanded: Boolean,
    anchorSize: IntSize,
    options: List<String>,
    focusable: Boolean = true,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (!expanded) return
    val menuWidth = if (anchorSize.width > 0) {
        with(androidx.compose.ui.platform.LocalDensity.current) { anchorSize.width.toDp() }
    } else {
        180.dp
    }
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.roundToPx() }

    Popup(
        popupPositionProvider = DeskDropdownPositionProvider(gapPx),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = focusable),
    ) {
        Column(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp),
        ) {
            options.forEach { option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeskAnchoredDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<String>,
    anchorSize: IntSize,
    minWidth: Dp,
    focusable: Boolean = true,
    onSelect: (String) -> Unit,
) {
    if (!expanded) return
    val menuWidth = if (anchorSize.width > 0) {
        with(androidx.compose.ui.platform.LocalDensity.current) { anchorSize.width.toDp() }
    } else {
        minWidth
    }
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.roundToPx() }

    Popup(
        popupPositionProvider = DeskDropdownPositionProvider(gapPx),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = focusable),
    ) {
        Column(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(DeskControlCornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(DeskControlCornerRadius))
                .padding(vertical = 4.dp),
        ) {
            options.forEach { option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private class DeskDropdownPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val belowY = anchorBounds.bottom + gapPx
        val aboveY = anchorBounds.top - popupContentSize.height - gapPx
        val y = when {
            belowY + popupContentSize.height <= windowSize.height -> belowY
            aboveY >= 0 -> aboveY
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }
        val x = anchorBounds.left.coerceIn(
            minimumValue = 0,
            maximumValue = (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        )
        return IntOffset(x, y)
    }
}
