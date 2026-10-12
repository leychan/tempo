package io.github.leychan.tempo.ui

import android.view.ViewGroup
import android.view.View
import android.os.Build
import android.widget.NumberPicker
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.leychan.tempo.domain.formatSeconds

@Composable
fun DurationPickerDialog(
    title: String,
    initialSeconds: Int,
    minimumSeconds: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = LocalTempoColors.current
    var minutes by rememberSaveable(title) { mutableIntStateOf(initialSeconds / 60) }
    var seconds by rememberSaveable(title) { mutableIntStateOf(initialSeconds % 60) }
    fun secondsRange(minute: Int): IntRange = when {
        minute == 60 -> 0..0
        minute == 0 -> minimumSeconds..59
        else -> 0..59
    }
    val selected = minutes * 60 + seconds
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(formatSeconds(selected.toLong()), fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Surface(color = colors.background, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                        WheelColumn("分钟", minutes, 0..60, Modifier.weight(1f)) { value ->
                            minutes = value
                            seconds = seconds.coerceIn(secondsRange(value))
                        }
                        WheelColumn("秒", seconds, secondsRange(minutes), Modifier.weight(1f)) { seconds = it }
                    }
                }
                Text(if (minimumSeconds > 0) "滑动选择时间 · 最少 1 秒，最多 60 分钟"
                    else "滑动选择时间 · 0 秒可跳过此阶段",
                    color = colors.muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun QuantityPickerDialog(
    title: String,
    initialValue: Int,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = LocalTempoColors.current
    var selected by rememberSaveable(title) { mutableIntStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("$selected $unit", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Surface(color = colors.background, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    WheelColumn(unit, selected, 1..99, Modifier.padding(vertical = 12.dp)) { selected = it }
                }
                Text("滑动选择数量 · 1～99 $unit", color = colors.muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun WheelColumn(label: String, selected: Int, range: IntRange, modifier: Modifier, onSelected: (Int) -> Unit) {
    val colors = LocalTempoColors.current
    val currentCallback by rememberUpdatedState(onSelected)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = colors.muted, fontSize = 12.sp)
        Box(Modifier.width(104.dp).height(156.dp).semantics(mergeDescendants = true) {
                contentDescription = if (label == "分钟" || label == "秒") label else "数量"
                stateDescription = "%02d".format(selected)
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), range.first.toFloat()..range.last.toFloat(),
                    (range.last - range.first - 1).coerceAtLeast(0))
                setProgress { target -> currentCallback(target.toInt().coerceIn(range)); true }
                customActions = listOf(
                    CustomAccessibilityAction("增加") { currentCallback((selected + 1).coerceIn(range)); true },
                    CustomAccessibilityAction("减少") { currentCallback((selected - 1).coerceIn(range)); true },
                )
            }) {
        key(colors.isDark) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val pickerTheme = if (colors.isDark) android.R.style.Theme_Material_NoActionBar
                    else android.R.style.Theme_Material_Light_NoActionBar
                NumberPicker(ContextThemeWrapper(context, pickerTheme)).apply {
                    minValue = range.first
                    maxValue = range.last
                    value = selected
                    setFormatter { "%02d".format(it) }
                    wrapSelectorWheel = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        textColor = colors.text.toArgb()
                        selectionDividerHeight = resources.displayMetrics.density.toInt().coerceAtLeast(1)
                    }
                    // Scrolling and accessibility actions select values without opening a keyboard.
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    contentDescription = if (label == "分钟" || label == "秒") label else "数量"
                    setOnValueChangedListener { _, _, newValue -> currentCallback(newValue) }
                }
            },
            update = { picker ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) picker.textColor = colors.text.toArgb()
                if (picker.minValue != range.first || picker.maxValue != range.last) {
                    picker.minValue = 0
                    picker.maxValue = range.last
                    picker.minValue = range.first
                }
                picker.value = selected.coerceIn(range)
            },
        )
        }
        }
    }
}
