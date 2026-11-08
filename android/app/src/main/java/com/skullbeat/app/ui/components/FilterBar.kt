package com.skullbeat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skullbeat.app.domain.util.FilterCriteria
import com.skullbeat.app.domain.util.TimeUtils
import com.skullbeat.app.ui.theme.CyanAccent
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted

@Composable
fun FilterBar(
    criteria: FilterCriteria,
    onCriteriaChange: (FilterCriteria) -> Unit,
    onExportM3u: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(10.dp)
    ) {
        // Main Search Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RaisedDark)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = criteria.query,
                    onValueChange = { onCriteriaChange(criteria.copy(query = it)) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, color = TextBone),
                    cursorBrush = SolidColor(ElectricBlue),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (criteria.query.isEmpty()) {
                            Text(
                                text = "Filter by title, artist, album...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                            )
                        }
                        innerTextField()
                    }
                )
                if (criteria.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onCriteriaChange(criteria.copy(query = "")) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Expand detailed filters toggle
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (expanded) ElectricBlue else RaisedDark)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter Options",
                    tint = if (expanded) TextBone else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Export button
            androidx.compose.foundation.layout.Box {
                IconButton(
                    onClick = { exportMenuOpen = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RaisedDark)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = exportMenuOpen,
                    onDismissRequest = { exportMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export as M3U Playlist") },
                        onClick = {
                            exportMenuOpen = false
                            onExportM3u()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export as CSV") },
                        onClick = {
                            exportMenuOpen = false
                            onExportCsv()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export as JSON") },
                        onClick = {
                            exportMenuOpen = false
                            onExportJson()
                        }
                    )
                }
            }
        }

        // Expanded Advanced Filters
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                // Artist & Album input fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniFilterField(
                        label = "Artist",
                        value = criteria.artist,
                        onValueChange = { onCriteriaChange(criteria.copy(artist = it)) },
                        modifier = Modifier.weight(1f)
                    )
                    MiniFilterField(
                        label = "Album",
                        value = criteria.album,
                        onValueChange = { onCriteriaChange(criteria.copy(album = it)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Duration Slider (0 to 600 seconds)
                val currentMax = if (criteria.maxSec == Long.MAX_VALUE) 600f else criteria.maxSec.toFloat()
                val sliderRange = criteria.minSec.toFloat()..currentMax

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Duration: ${TimeUtils.formatDuration(criteria.minSec)} - ${TimeUtils.formatDuration(currentMax.toLong())}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (criteria != FilterCriteria()) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall.copy(color = ElectricBlue),
                            modifier = Modifier.clickable { onCriteriaChange(FilterCriteria()) }
                        )
                    }
                }

                RangeSlider(
                    value = sliderRange,
                    onValueChange = { range ->
                        onCriteriaChange(
                            criteria.copy(
                                minSec = range.start.toLong(),
                                maxSec = if (range.endInclusive >= 595f) Long.MAX_VALUE else range.endInclusive.toLong()
                            )
                        )
                    },
                    valueRange = 0f..600f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = RaisedDark
                    )
                )
            }
        }
    }
}

@Composable
private fun MiniFilterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(RaisedDark)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextBone),
            cursorBrush = SolidColor(ElectricBlue),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(text = label, style = MaterialTheme.typography.labelSmall)
                }
                innerTextField()
            }
        )
    }
}
