package com.diffusiondesk.desktop.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.ui.component.Text

internal val DeskLayoutGap = 8.dp
internal val DeskScreenPadding = DeskLayoutGap
internal val DeskPanelPadding = 14.dp
internal val DeskPanelSpacing = DeskLayoutGap
internal val DeskSectionSpacing = DeskLayoutGap
internal val DeskControlSpacing = DeskLayoutGap
internal val DeskCompactControlSpacing = 6.dp
internal val DeskGroupSpacing = DeskLayoutGap
internal val DeskTabSpacing = DeskLayoutGap
internal val DeskTabHorizontalPadding = 10.dp
internal val DeskIconSize = 16.dp
internal val DeskPanelCornerRadius = 8.dp
internal val DeskControlCornerRadius = 6.dp
internal const val DeskSubtleSurfaceAlpha = 0.45f
internal const val DeskSelectedContainerAlpha = 0.16f

internal data class DeskTabItem(
    val selected: Boolean,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
internal fun DeskPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(DeskPanelCornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(DeskPanelPadding),
        verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
        content = content,
    )
}

@Composable
internal fun DeskTabHeader(
    tabs: List<DeskTabItem>,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskPanelCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = DeskPanelPadding, vertical = DeskControlSpacing),
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
    val shape = RoundedCornerShape(5.dp)
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    DeskTooltip(text = tooltip) {
        Box(
            modifier = modifier
                .size(32.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
                .then(if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = tint,
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
