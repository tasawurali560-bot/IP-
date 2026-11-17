package com.example.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.HardwareTelemetryRepository
import com.example.system.RamTelemetry
import com.example.system.SimPreferences
import com.example.system.SystemStatusSnapshot
import com.example.system.formatBytesHuman
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.components.SimulationNoticePill
import com.example.ui.components.SimulatorTopBar
import com.example.ui.components.SpecComparisonBadge
import com.example.ui.components.TelemetryKeyValueRow
import com.example.ui.theme.DesertTitanium
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ProAmber
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun DeviceSettingsScreen(
    onBack: () -> Unit,
    onOpenStorageDashboard: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { HardwareTelemetryRepository(context) }
    val prefs by repo.preferencesFlow.collectAsStateWithLifecycle(initialValue = SimPreferences())

    var ram by remember { mutableStateOf(RamTelemetry()) }
    var sysStatus by remember { mutableStateOf(SystemStatusSnapshot()) }
    var optimizeFeedback by remember { mutableStateOf<String?>(null) }
    val ramHistory = remember { mutableStateListOf<Float>() }
    val heapHistory = remember { mutableStateListOf<Float>() }

    // Lifecycle-scoped telemetry sampling while Settings > Device Information is visible
    LaunchedEffect(Unit) {
        while (isActive) {
            val sample = repo.readRamTelemetry()
            ram = sample
            sysStatus = repo.readSystemStatus()

            ramHistory.add(sample.actualUsageFraction)
            if (ramHistory.size > 30) ramHistory.removeAt(0)

            val heapFrac = if (sample.jvmHeapMaxBytes > 0L) {
                (sample.jvmHeapUsedBytes.toFloat() / sample.jvmHeapMaxBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f
            heapHistory.add(heapFrac)
            if (heapHistory.size > 30) heapHistory.removeAt(0)

            delay(1200)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SimulatorTopBar(
            title = "Settings • Device Info",
            subtitle = "iPhone 17 Pro Max Virtual Simulator",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("settings_list"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SimulationNoticePill(
                    text = "Virtual RAM (16 GB) and Virtual Storage (1 TB) are simulated specifications. They do not increase physical Android RAM or storage."
                )
            }

            // Device Identity Card
            item {
                LiquidGlassCard {
                    Text("Device Information", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryKeyValueRow("Simulator Profile", "iPhone 17 Pro Max Virtual Simulator", DesertTitanium)
                    TelemetryKeyValueRow("Virtual OS Shell", "iOS 26.0 Inspired")
                    TelemetryKeyValueRow("Virtual RAM", "16.0 GB (Simulated)", DesertTitanium)
                    TelemetryKeyValueRow("Virtual Storage", "1024 GB / 1 TB (Simulated)", DesertTitanium)
                    Spacer(modifier = Modifier.height(6.dp))
                    TelemetryKeyValueRow("Host Android Device", ram.deviceModel, SapphireBlue)
                    TelemetryKeyValueRow("Host Android OS", ram.androidVersion)
                    TelemetryKeyValueRow("CPU Cores", "${ram.cpuCores} Cores")
                    TelemetryKeyValueRow("Thermal State", ram.thermalStatusLabel, EmeraldLive)
                    TelemetryKeyValueRow("Battery", "${sysStatus.batteryPercent}% ${if (sysStatus.isCharging) "(Charging)" else ""}")
                    TelemetryKeyValueRow("Network", sysStatus.networkLabel)
                }
            }

            // 16GB Virtual RAM vs Actual Android RAM Dashboard
            item {
                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RAM & Memory Dashboard", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        SpecComparisonBadge(label = "16 GB VIRTUAL", isSimulated = true)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Virtual RAM Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Virtual RAM (Simulated)", style = MaterialTheme.typography.bodyMedium, color = DesertTitanium)
                        Text(
                            "${String.format("%.1f", ram.virtualAllocatedGb)} / 16.0 GB",
                            style = MaterialTheme.typography.labelMedium,
                            color = DesertTitanium,
                            modifier = Modifier.testTag("virtual_ram_value")
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SegmentedProgressBar(fraction = ram.virtualUsageFraction, color = DesertTitanium)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Actual Physical Android RAM Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpecComparisonBadge(label = "ACTUAL ANDROID RAM", isSimulated = false)
                        Text(
                            "${formatBytesHuman(ram.actualUsedBytes)} / ${formatBytesHuman(ram.actualTotalBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SapphireBlue,
                            modifier = Modifier.testTag("actual_ram_value")
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedProgressBar(fraction = ram.actualUsageFraction, color = SapphireBlue)

                    Spacer(modifier = Modifier.height(10.dp))
                    TelemetryKeyValueRow("Available Android RAM", formatBytesHuman(ram.actualAvailableBytes), EmeraldLive)
                    TelemetryKeyValueRow("Low Memory State", if (ram.actualLowMemory) "Yes (Constrained)" else "Normal")
                    TelemetryKeyValueRow("App JVM Heap Used", "${formatBytesHuman(ram.jvmHeapUsedBytes)} / ${formatBytesHuman(ram.jvmHeapMaxBytes)}")
                    TelemetryKeyValueRow("App Native + Dalvik PSS", "${ram.appTotalPssKb / 1024} MB (${ram.appTotalPssKb} KB)")

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val freed = repo.optimizeMemoryAndClearCache()
                                ram = repo.readRamTelemetry()
                                optimizeFeedback = "Cache trimmed (${formatBytesHuman(freed)} freed)"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SapphireBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("optimize_memory_button")
                    ) {
                        Text("Optimize App Memory & Trim Cache")
                    }
                    optimizeFeedback?.let { msg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(msg, style = MaterialTheme.typography.labelSmall, color = EmeraldLive)
                    }
                }
            }

            // Live Memory Usage Graph
            item {
                LiquidGlassCard {
                    Text("Live Memory Telemetry Graph", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Blue: Physical Android RAM %  •  Amber: App Heap %",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MemoryUsageLineGraph(
                        ramSamples = ramHistory.toList(),
                        heapSamples = heapHistory.toList(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }

            // Performance & Storage Warning Preferences
            item {
                LiquidGlassCard {
                    Text("Performance & Storage Controls", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Simulator Performance Mode", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Efficiency", "Balanced", "Pro").forEach { mode ->
                            val selected = prefs.performanceMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) SapphireBlue.copy(alpha = 0.25f) else ObsidianBg)
                                    .border(1.dp, if (selected) SapphireBlue else GlassBorder, RoundedCornerShape(12.dp))
                                    .clickable { scope.launch { repo.updatePerformanceMode(mode) } }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) SapphireBlue else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Storage Warning Threshold: ${prefs.storageWarningThresholdPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Slider(
                        value = prefs.storageWarningThresholdPercent.toFloat(),
                        onValueChange = { v ->
                            scope.launch { repo.updateStorageWarningThreshold(v.toInt()) }
                        },
                        valueRange = 50f..98f,
                        colors = SliderDefaults.colors(thumbColor = ProAmber, activeTrackColor = ProAmber)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onOpenStorageDashboard,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open 1TB Virtual Storage & File Manager", color = DesertTitanium)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MemoryUsageLineGraph(
    ramSamples: List<Float>,
    heapSamples: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        val h = size.height
        val w = size.width

        // Horizontal reference lines (25%, 50%, 75%)
        listOf(0.25f, 0.5f, 0.75f).forEach { level ->
            val y = h * (1f - level)
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        fun drawSeries(samples: List<Float>, color: Color, fillArea: Boolean) {
            if (samples.size < 2) return
            val path = Path()
            val fillPath = Path()
            val stepX = w / (samples.size - 1).coerceAtLeast(1)
            samples.forEachIndexed { idx, value ->
                val x = idx * stepX
                val y = h * (1f - value.coerceIn(0f, 1f))
                if (idx == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            if (fillArea) {
                fillPath.lineTo((samples.size - 1) * stepX, h)
                fillPath.close()
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }

        drawSeries(ramSamples, SapphireBlue, fillArea = true)
        drawSeries(heapSamples, ProAmber, fillArea = false)
    }
}
