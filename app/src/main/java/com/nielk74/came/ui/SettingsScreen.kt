package com.nielk74.came.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nielk74.came.filters.FilmCatalog
import com.nielk74.came.filters.FilmProfile
import com.nielk74.came.settings.CameraSettings

private val MenuBlack = Color(0xFF070707)
private val PanelBlack = Color(0xFF111111)
private val Separator = Color(0xFF2B2B2B)
private val Muted = Color(0xFF929292)
private val FujiRed = Color(0xFFE31B23)

/** Full-screen, black camera menu with sparse typography and a Fujifilm-inspired red selection. */
@Composable
fun SettingsScreen(
    settings: CameraSettings,
    profiles: List<FilmProfile> = FilmCatalog.profiles,
    updateStatus: String? = null,
    updateActionLabel: String = "CHECK FOR UPDATES",
    isCheckingForUpdates: Boolean = false,
    onGrainChanged: (Boolean) -> Unit,
    onFilterEnabledChanged: (String, Boolean) -> Unit,
    onTimerChanged: (Int) -> Unit,
    onOpenGallery: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Surface(modifier = Modifier.fillMaxSize(), color = MenuBlack) {
        Column(modifier = Modifier.fillMaxSize()) {
            MenuHeader(onClose)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 36.dp),
            ) {
                SectionLabel("MEDIA")
                LibraryRow(onClick = onOpenGallery)
                SectionLabel("IMAGE")
                ToggleRow(
                    title = "FILM GRAIN",
                    subtitle = "Tone-driven, stock-specific texture",
                    checked = settings.grainEnabled,
                    onCheckedChange = onGrainChanged,
                )
                HorizontalDivider(color = Separator, modifier = Modifier.padding(start = 24.dp))
                SectionLabel("FILM PROFILES")
                profiles.forEachIndexed { index, profile ->
                    val checked = profile.id in settings.enabledFilterIds
                    FilterRow(
                        profile = profile,
                        checked = checked,
                        selected = profile.id == settings.selectedFilterId,
                        canToggle = !checked || settings.enabledFilterIds.size > 1,
                        onCheckedChange = { onFilterEnabledChanged(profile.id, it) },
                    )
                    if (index != profiles.lastIndex) {
                        HorizontalDivider(color = Separator, modifier = Modifier.padding(start = 76.dp))
                    }
                }
                SectionLabel("CAPTURE")
                TimerRow(settings.timerSeconds, onTimerChanged)
                SectionLabel("APPLICATION")
                UpdateRow(
                    actionLabel = updateActionLabel,
                    status = updateStatus,
                    checking = isCheckingForUpdates,
                    onClick = onCheckForUpdates,
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBlack)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = FujiRed,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.width(16.dp))
        LabelBlock(
            title = "PHOTO LIBRARY",
            subtitle = "View, zoom, share and manage photographs",
            modifier = Modifier.weight(1f),
        )
        Text(text = "›", color = Muted, fontSize = 28.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun MenuHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color.Black)
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(28.dp).background(FujiRed))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CAMÉ",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(text = "SHOOTING SETTINGS", color = Muted, fontSize = 10.sp, letterSpacing = 1.3.sp)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close settings", tint = Color.White)
        }
    }
    HorizontalDivider(color = Separator)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = FujiRed,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 10.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBlack)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelBlock(title, subtitle, Modifier.weight(1f))
        MenuSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FilterRow(
    profile: FilmProfile,
    checked: Boolean,
    selected: Boolean,
    canToggle: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF171313) else PanelBlack)
            .padding(start = 20.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 34.dp)
                .background(if (selected) FujiRed else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(profile.swatchTop), Color(profile.swatchBottom)),
                    ),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = profile.displayName.uppercase(),
            color = if (checked) Color.White else Muted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = .7.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MenuSwitch(
            checked = checked,
            enabled = canToggle,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun TimerRow(selectedSeconds: Int, onSelected: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBlack)
            .padding(horizontal = 24.dp, vertical = 17.dp),
    ) {
        LabelBlock("SELF TIMER", "Delay before shutter release")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CameraSettings.TIMER_CHOICES.forEach { seconds ->
                val selected = selectedSeconds == seconds
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) FujiRed else Color(0xFF242424))
                        .clickable { onSelected(seconds) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (seconds == 0) "OFF" else "${seconds}S",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .8.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    actionLabel: String,
    status: String?,
    checking: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBlack)
            .clickable(enabled = !checking, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.SystemUpdateAlt,
            contentDescription = null,
            tint = if (checking) Muted else FujiRed,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.width(16.dp))
        LabelBlock(
            title = actionLabel,
            subtitle = status ?: "Install the latest public release",
            modifier = Modifier.weight(1f),
        )
        if (checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                color = FujiRed,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = "›", color = Muted, fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun LabelBlock(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = .7.sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(text = subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MenuSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = FujiRed,
            uncheckedThumbColor = Color(0xFFB5B5B5),
            uncheckedTrackColor = Color(0xFF3A3A3A),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = FujiRed.copy(alpha = .45f),
        ),
    )
}
