package com.example.mental_healt_chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

enum class ChartRange { DAY, WEEK, MONTH }
enum class EmotionFilter { ALL, HAPPY, SAD, ANXIOUS, ANGRY, NEUTRAL }

private data class BucketAvg(
    val label: String,
    val happy: Float,
    val sad: Float,
    val anxious: Float,
    val angry: Float,
    val neutral: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionChartScreen(
    api: ApiService,
    onAuthExpired: () -> Unit,
    onBack: () -> Unit,
) {
    var range by remember { mutableStateOf(ChartRange.DAY) }
    var filter by remember { mutableStateOf(EmotionFilter.ALL) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var buckets by remember { mutableStateOf<List<BucketAvg>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val ro = Locale("ro", "RO")

    fun computeWindow(r: ChartRange): Pair<Instant, Instant> {
        val now = ZonedDateTime.now(zone)
        return when (r) {
            ChartRange.DAY -> {
                val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
                startOfDay.toInstant() to now.toInstant()
            }
            ChartRange.WEEK -> {
                val start = now.truncatedTo(ChronoUnit.DAYS).minusDays(6)
                start.toInstant() to now.toInstant()
            }
            ChartRange.MONTH -> {
                val start = now.truncatedTo(ChronoUnit.DAYS).minusDays(29)
                start.toInstant() to now.toInstant()
            }
        }
    }

    fun bucketLabel(inst: Instant, r: ChartRange): String {
        val zdt = inst.atZone(zone)
        return when (r) {
            ChartRange.DAY ->
                zdt.truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("HH:00"))
            ChartRange.WEEK ->
                zdt.toLocalDate()
                    .format(DateTimeFormatter.ofPattern("EEE dd", ro)) // Lun 03, Mar 04...
            ChartRange.MONTH ->
                zdt.toLocalDate()
                    .format(DateTimeFormatter.ofPattern("dd MMM", ro)) // 03 ian, 04 feb...
        }
    }

    fun aggregate(entries: List<MoodEntryDto>, r: ChartRange): List<BucketAvg> {
        data class Acc(
            var n: Int = 0,
            var h: Int = 0,
            var s: Int = 0,
            var ax: Int = 0,
            var ag: Int = 0,
            var ne: Int = 0
        )

        val map = linkedMapOf<String, Acc>()

        for (e in entries) {
            val inst = Instant.parse(e.created_at)
            val key = bucketLabel(inst, r)
            val a = map.getOrPut(key) { Acc() }
            a.n += 1
            a.h += e.happy
            a.s += e.sad
            a.ax += e.anxious
            a.ag += e.angry
            a.ne += e.neutral
        }

        return map.map { (label, a) ->
            val n = max(1, a.n).toFloat()
            BucketAvg(
                label = label,
                happy = a.h / n,
                sad = a.s / n,
                anxious = a.ax / n,
                angry = a.ag / n,
                neutral = a.ne / n
            )
        }
    }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val (from, to) = computeWindow(range)

                val entries = api.getMoodEntries(
                    fromIso = from.toString(),
                    toIso = to.toString()
                )

                buckets = aggregate(entries, range)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onAuthExpired()
                else error = "Eroare server: HTTP ${e.code()}"
            } catch (_: Exception) {
                error = "Serverul nu este accesibil."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(range) { load() }

    val labels = buckets.map { it.label }

    val happyEntries   = buckets.mapIndexed { i, b -> FloatEntry(i.toFloat(), b.happy) }
    val sadEntries     = buckets.mapIndexed { i, b -> FloatEntry(i.toFloat(), b.sad) }
    val anxiousEntries = buckets.mapIndexed { i, b -> FloatEntry(i.toFloat(), b.anxious) }
    val angryEntries   = buckets.mapIndexed { i, b -> FloatEntry(i.toFloat(), b.angry) }
    val neutralEntries = buckets.mapIndexed { i, b -> FloatEntry(i.toFloat(), b.neutral) }

    val happyColor   = Color(0xFF00C853)
    val sadColor     = Color(0xFF2979FF)
    val anxiousColor = Color(0xFFFF9100)
    val angryColor   = Color(0xFFD50000)
    val neutralColor = Color(0xFFAA00FF)

    val lastX = max(0, buckets.size - 1).toFloat()
    val rangeEntries = listOf(FloatEntry(0f, 0f), FloatEntry(lastX, 100f))
    val transparentColor = Color(0x00000000)

    val usedChart = when (filter) {
        EmotionFilter.ALL -> lineChart(lines = listOf(
            lineSpec(lineColor = transparentColor),
            lineSpec(lineColor = happyColor),
            lineSpec(lineColor = sadColor),
            lineSpec(lineColor = anxiousColor),
            lineSpec(lineColor = angryColor),
            lineSpec(lineColor = neutralColor)
        ))
        EmotionFilter.HAPPY   -> lineChart(lines = listOf(lineSpec(lineColor = transparentColor), lineSpec(lineColor = happyColor)))
        EmotionFilter.SAD     -> lineChart(lines = listOf(lineSpec(lineColor = transparentColor), lineSpec(lineColor = sadColor)))
        EmotionFilter.ANXIOUS -> lineChart(lines = listOf(lineSpec(lineColor = transparentColor), lineSpec(lineColor = anxiousColor)))
        EmotionFilter.ANGRY   -> lineChart(lines = listOf(lineSpec(lineColor = transparentColor), lineSpec(lineColor = angryColor)))
        EmotionFilter.NEUTRAL -> lineChart(lines = listOf(lineSpec(lineColor = transparentColor), lineSpec(lineColor = neutralColor)))
    }

    val model = when (filter) {
        EmotionFilter.ALL     -> entryModelOf(rangeEntries, happyEntries, sadEntries, anxiousEntries, angryEntries, neutralEntries)
        EmotionFilter.HAPPY   -> entryModelOf(rangeEntries, happyEntries)
        EmotionFilter.SAD     -> entryModelOf(rangeEntries, sadEntries)
        EmotionFilter.ANXIOUS -> entryModelOf(rangeEntries, anxiousEntries)
        EmotionFilter.ANGRY   -> entryModelOf(rangeEntries, angryEntries)
        EmotionFilter.NEUTRAL -> entryModelOf(rangeEntries, neutralEntries)
    }

    @Composable
    fun prettyChipColors() = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Grafic Emoții", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = { load() }, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reîncarcă")
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(error!!, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            // Card grafic principal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(14.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Intensitate (%)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(when (range) {
                                    ChartRange.DAY   -> "Azi"
                                    ChartRange.WEEK  -> "Ultimele 7 zile"
                                    ChartRange.MONTH -> "Ultimele 30 zile"
                                })
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (buckets.isEmpty() && !isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nu există date pentru intervalul selectat.")
                        }
                    } else if (buckets.isNotEmpty()) {
                        Chart(
                            chart = usedChart,
                            model = model,
                            startAxis = rememberStartAxis(
                                itemPlacer = remember { AxisItemPlacer.Vertical.default(maxItemCount = 11) },
                                valueFormatter = { value, _ -> "${value.toInt()}%" }
                            ),
                            // FIX 3: coerceIn ca să nu iasă din bounds
                            bottomAxis = rememberBottomAxis(
                                valueFormatter = { x, _ ->
                                    labels.getOrNull(
                                        x.toInt().coerceIn(0, (labels.size - 1).coerceAtLeast(0))
                                    ) ?: ""
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().height(290.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        // Legendă
                        if (filter == EmotionFilter.ALL) {
                            LegendRow(happyColor, sadColor, anxiousColor, angryColor, neutralColor)
                        } else {
                            val (name, col) = when (filter) {
                                EmotionFilter.HAPPY   -> "Fericit" to happyColor
                                EmotionFilter.SAD     -> "Trist" to sadColor
                                EmotionFilter.ANXIOUS -> "Anxios" to anxiousColor
                                EmotionFilter.ANGRY   -> "Supărat" to angryColor
                                EmotionFilter.NEUTRAL -> "Neutru" to neutralColor
                                EmotionFilter.ALL     -> "Toate" to Color.Unspecified
                            }
                            LegendItem(name, col)
                        }

                        Spacer(Modifier.height(14.dp))

                        Text("Filtrare emoție", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = filter == EmotionFilter.ALL,     onClick = { filter = EmotionFilter.ALL },     label = { Text("Toate") },   colors = prettyChipColors())
                            FilterChip(selected = filter == EmotionFilter.HAPPY,   onClick = { filter = EmotionFilter.HAPPY },   label = { Text("Fericit") }, colors = prettyChipColors())
                            FilterChip(selected = filter == EmotionFilter.SAD,     onClick = { filter = EmotionFilter.SAD },     label = { Text("Trist") },   colors = prettyChipColors())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = filter == EmotionFilter.ANXIOUS, onClick = { filter = EmotionFilter.ANXIOUS }, label = { Text("Anxios") },  colors = prettyChipColors())
                            FilterChip(selected = filter == EmotionFilter.ANGRY,   onClick = { filter = EmotionFilter.ANGRY },   label = { Text("Supărat") }, colors = prettyChipColors())
                            FilterChip(selected = filter == EmotionFilter.NEUTRAL, onClick = { filter = EmotionFilter.NEUTRAL }, label = { Text("Neutru") },  colors = prettyChipColors())
                        }
                    }
                }
            }

            // Card interval timp
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Interval de timp", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = range == ChartRange.DAY,
                            onClick = { range = ChartRange.DAY },
                            label = { Text("Azi") },
                            colors = prettyChipColors(),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = range == ChartRange.WEEK,
                            onClick = { range = ChartRange.WEEK },
                            label = { Text("Săpt.") },
                            colors = prettyChipColors(),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = range == ChartRange.MONTH,
                            onClick = { range = ChartRange.MONTH },
                            label = { Text("Lună") },
                            colors = prettyChipColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Înapoi")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(
    happyColor: Color, sadColor: Color, anxiousColor: Color,
    angryColor: Color, neutralColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem("Fericit", happyColor)
            LegendItem("Trist", sadColor)
            LegendItem("Anxios", anxiousColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem("Supărat", angryColor)
            LegendItem("Neutru", neutralColor)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
