package io.github.leychan.tempo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leychan.tempo.TimerUiState
import io.github.leychan.tempo.TimerViewModel
import io.github.leychan.tempo.domain.*
import java.util.UUID

@Composable
fun TempoApp(state: TimerUiState, viewModel: TimerViewModel) {
    val colors = LocalTempoColors.current
    var page by rememberSaveable { mutableStateOf("timer") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnPage by rememberSaveable { mutableStateOf("timer") }
    var pendingSelection by remember { mutableStateOf<TrainingPlan?>(null) }
    var pendingDeletion by remember { mutableStateOf<TrainingPlan?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    fun edit(plan: TrainingPlan?) {
        returnPage = page
        editingId = plan?.id ?: UUID.randomUUID().toString()
        page = "edit"
    }
    BackHandler(enabled = page == "plans") { page = "timer" }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (page != "edit") {
                Surface(color = colors.surface, shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NavigationPill("训练", Icons.Rounded.Timer, page == "timer", Modifier.weight(1f)) { page = "timer" }
                        NavigationPill("我的计划", Icons.Rounded.GridView, page == "plans", Modifier.weight(1f)) { page = "plans" }
                    }
                }
            }
        },
    ) { padding ->
        when (page) {
            "timer" -> TimerScreen(state, Modifier.padding(padding),
                onSound = viewModel::toggleSound, onTheme = viewModel::setThemeMode, onEdit = { edit(state.selectedPlan) },
                onToggle = viewModel::toggleTimer,
                onReset = {
                    if (state.hasSession && state.timer.elapsedMillis > 0) confirmReset = true
                    else viewModel.reset()
                })
            "plans" -> PlansScreen(state, Modifier.padding(padding),
                onAdd = { edit(null) }, onEdit = { edit(it) },
                onDelete = { pendingDeletion = it },
                onSelect = { plan ->
                    if (plan.id != state.selectedPlan.id && state.hasSession) pendingSelection = plan
                    else { viewModel.selectPlan(plan.id); page = "timer" }
                })
            "edit" -> {
                val plan = state.plans.firstOrNull { it.id == editingId }
                    ?: TrainingPlan(id = editingId!!, name = "")
                PlanEditor(plan, state.plans.any { it.id == editingId }, Modifier.padding(padding),
                    onCancel = { page = returnPage },
                    onSave = { updated ->
                        val saved = viewModel.savePlan(updated)
                        if (saved) page = returnPage
                        saved
                    })
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog("重新开始训练？", "当前进度将清零，训练计划仍会保留。", "重置",
            onDismiss = { confirmReset = false },
            onConfirm = { viewModel.reset(); confirmReset = false })
    }
    pendingSelection?.let { plan ->
        ConfirmDialog("切换训练计划？", "当前训练将结束，切换至「${plan.name}」后可重新开始。", "切换",
            onDismiss = { pendingSelection = null },
            onConfirm = { viewModel.selectPlan(plan.id); pendingSelection = null; page = "timer" })
    }
    pendingDeletion?.let { plan ->
        ConfirmDialog("删除这个计划？", "「${plan.name}」将从本机删除。", "删除",
            onDismiss = { pendingDeletion = null },
            onConfirm = { viewModel.deletePlan(plan.id); pendingDeletion = null })
    }
}

@Composable
private fun NavigationPill(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalTempoColors.current
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) colors.primaryContainer else Color.Transparent,
            contentColor = if (selected) colors.primary else colors.muted)) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimerScreen(
    state: TimerUiState, modifier: Modifier,
    onSound: () -> Unit, onTheme: (ThemeMode) -> Unit, onEdit: () -> Unit, onToggle: () -> Unit, onReset: () -> Unit,
) {
    val colors = LocalTempoColors.current
    val plan = state.selectedPlan
    val timer = state.timer
    val complete = timer.status == TimerStatus.COMPLETED
    val phase = timer.phase
    val accent = if (complete) Color(0xFFB8EDB4) else stageColor(phase?.stage ?: Stage.PREPARE)
    BoxWithConstraints(modifier.fillMaxSize()) {
      val ringDiameter = (maxHeight - 460.dp).coerceIn(120.dp, 180.dp)
      Column(Modifier.fillMaxSize()) {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("间歇训练", style = MaterialTheme.typography.headlineLarge)
            }
            ThemeControl(state.themeMode, onTheme)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = onSound, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.surface, contentColor = colors.text)) {
                Icon(if (state.soundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = if (state.soundEnabled) "关闭提示音" else "开启提示音")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${plan.cycles} 周期 × ${plan.rounds} 循环 · 总时长 ${formatSeconds(plan.totalSeconds)}",
                    style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            TextButton(onClick = onEdit, enabled = !state.hasSession) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("调整")
            }
        }

        Surface(shape = RoundedCornerShape(28.dp), color = colors.timerCard, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                        Text(if (complete) "训练完成" else phase?.stage?.title.orEmpty(),
                            color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Text(when (timer.status) {
                        TimerStatus.IDLE -> "待开始"
                        TimerStatus.RUNNING -> "进行中"
                        TimerStatus.PAUSED -> "已暂停"
                        TimerStatus.COMPLETED -> "做得很好"
                    }, color = colors.timerMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val ringSize = minOf(maxWidth, ringDiameter)
                    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(color = colors.timerText.copy(alpha = 0.09f), style = stroke)
                            drawArc(color = accent, startAngle = -90f,
                                sweepAngle = if (complete) 360f else 360f * (1f - timer.phaseProgress),
                                useCenter = false, style = stroke)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (complete) "已完成" else "阶段剩余", color = colors.timerMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(formatSeconds(timer.phaseRemainingSeconds), color = colors.timerText,
                                fontSize = when {
                                    formatSeconds(timer.phaseRemainingSeconds).length > 5 -> 30.sp
                                    ringSize < 160.dp -> 36.sp
                                    else -> 48.sp
                                },
                                fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-2).sp, maxLines = 1)
                            if (ringSize >= 160.dp) {
                                Spacer(Modifier.height(5.dp))
                                Text(if (complete) "每一份坚持，都有意义" else phase?.stage?.instruction.orEmpty(),
                                    color = colors.timerMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("整体进度", color = colors.timerMuted, fontSize = 12.sp)
                    Text("剩余 ${formatSeconds(timer.totalRemainingSeconds)}", color = colors.timerText, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { timer.totalProgress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = accent, trackColor = colors.timerText.copy(alpha = 0.09f))
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = colors.timerText.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(if (complete) Icons.Rounded.Check else Icons.Rounded.SkipNext, null,
                            tint = accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (complete) "本次训练已结束" else "下一阶段", color = colors.timerMuted, fontSize = 11.sp)
                        Text(if (complete) "休息一下，下次继续" else timer.nextPhase?.stage?.title ?: "训练完成",
                            color = colors.timerText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    timer.nextPhase?.let {
                        Text(formatSeconds(it.seconds.toLong()), color = accent, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressCard("当前周期", phase?.cycle ?: plan.cycles, plan.cycles, Icons.Rounded.Repeat, Modifier.weight(1f))
            ProgressCard("当前循环", phase?.round ?: plan.rounds, plan.rounds, Icons.Rounded.Layers, Modifier.weight(1f))
        }

      }
      Surface(color = colors.background, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReset, modifier = Modifier.width(64.dp).heightIn(min = 58.dp),
                shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "重置训练")
            }
            Button(onClick = onToggle, modifier = Modifier.weight(1f).heightIn(min = 58.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(if (timer.status == TimerStatus.RUNNING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(when (timer.status) {
                    TimerStatus.IDLE -> "开始训练"
                    TimerStatus.RUNNING -> "暂停训练"
                    TimerStatus.PAUSED -> "继续训练"
                    TimerStatus.COMPLETED -> "再练一次"
                }, fontSize = 17.sp)
            }
        }
        Text(if (state.backgroundPaused) "已为你暂停训练，点击「继续训练」即可恢复。"
            else if (state.hasSession) "训练中参数已锁定，重置后可调整。"
            else "训练时屏幕保持常亮 · 离开应用自动暂停",
            color = colors.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
      }
    }
    }
}

@Composable
private fun ThemeControl(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = LocalTempoColors.current
    var expanded by remember { mutableStateOf(false) }
    fun icon(mode: ThemeMode): ImageVector = when (mode) {
        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.DARK -> Icons.Rounded.DarkMode
    }
    Box {
        FilledIconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.surface, contentColor = colors.text)) {
            Icon(icon(selected), contentDescription = "选择界面主题")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = colors.surface) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.title, color = if (mode == selected) colors.primary else colors.text) },
                    leadingIcon = { Icon(icon(mode), null, tint = if (mode == selected) colors.primary else colors.muted) },
                    trailingIcon = { if (mode == selected) Icon(Icons.Rounded.Check, null, tint = colors.primary) },
                    onClick = { onSelect(mode); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(label: String, value: Int, total: Int, icon: ImageVector, modifier: Modifier) {
    val colors = LocalTempoColors.current
    Surface(modifier, color = colors.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = colors.muted)
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, color = colors.muted)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%02d".format(value), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(" / %02d".format(total), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun PlansScreen(
    state: TimerUiState, modifier: Modifier, onAdd: () -> Unit,
    onEdit: (TrainingPlan) -> Unit, onDelete: (TrainingPlan) -> Unit, onSelect: (TrainingPlan) -> Unit,
) {
    val colors = LocalTempoColors.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("为每一次训练做好准备", color = colors.primary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("我的计划", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("${state.plans.size} 个计划 · 保存在本机", color = colors.muted, fontSize = 13.sp)
                }
                FilledIconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Add, "新增训练计划")
                }
            }
        }
        items(state.plans, key = { it.id }) { plan ->
            val selected = plan.id == state.selectedPlan.id
            val locked = selected && state.hasSession
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(if (selected) colors.primaryContainer else colors.background,
                            RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FitnessCenter, null, tint = if (selected) colors.primary else colors.muted, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("总时长 ${formatSeconds(plan.totalSeconds)}", color = colors.muted, fontSize = 12.sp)
                        }
                        if (selected) {
                            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text("当前", color = colors.primary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PlanMetric("锻炼", formatSeconds(plan.workSeconds.toLong()))
                        PlanMetric("休息", formatSeconds(plan.restSeconds.toLong()))
                        PlanMetric("周期 × 循环", "${plan.cycles} × ${plan.rounds}")
                    }
                    HorizontalDivider(color = colors.outline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEdit(plan) }, enabled = !locked) {
                            Icon(Icons.Rounded.Edit, "编辑${plan.name}", Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onDelete(plan) }, enabled = !locked && state.plans.size > 1) {
                            Icon(Icons.Rounded.DeleteOutline, "删除${plan.name}", Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSelect(plan) }) {
                            Text(if (selected) "去训练" else "使用计划")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                        }
                    }
                    if (locked) Text("训练进行中，重置后可编辑或删除", color = colors.muted, fontSize = 11.sp)
                }
            }
        }
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("创建新的训练计划")
            }
            Text("至少保留一个计划，方便随时开始训练。", color = colors.muted, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlanMetric(label: String, value: String) {
    val colors = LocalTempoColors.current
    Column {
        Text(label, color = colors.muted, fontSize = 11.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlanEditor(base: TrainingPlan, existing: Boolean, modifier: Modifier,
    onCancel: () -> Unit, onSave: (TrainingPlan) -> Boolean) {
    val colors = LocalTempoColors.current
    var name by rememberSaveable(base.id) { mutableStateOf(base.name) }
    var prepare by rememberSaveable(base.id) { mutableIntStateOf(base.prepareSeconds) }
    var work by rememberSaveable(base.id) { mutableIntStateOf(base.workSeconds) }
    var rest by rememberSaveable(base.id) { mutableIntStateOf(base.restSeconds) }
    var cycles by rememberSaveable(base.id) { mutableIntStateOf(base.cycles) }
    var rounds by rememberSaveable(base.id) { mutableIntStateOf(base.rounds) }
    var roundRest by rememberSaveable(base.id) { mutableIntStateOf(base.roundRestSeconds) }
    var activePicker by rememberSaveable(base.id) { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    fun openPicker(field: String) { focusManager.clearFocus(); activePicker = field }
    var showErrors by rememberSaveable(base.id) { mutableStateOf(false) }
    var saveError by rememberSaveable(base.id) { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val draft = base.copy(name = name.trim(), prepareSeconds = prepare,
        workSeconds = work, restSeconds = rest, cycles = cycles, rounds = rounds,
        roundRestSeconds = roundRest)
    val error = draft.validationError()
    val dirty = name != base.name || draft != base
    fun cancel() { if (dirty) discard = true else onCancel() }
    BackHandler { cancel() }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { cancel() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text(if (existing) "编辑计划" else "新建计划", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("安排节奏，专注训练。", color = colors.muted, fontSize = 14.sp)
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 30) name = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("计划名称") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), isError = showErrors && name.isBlank(),
                supportingText = { Text("给这次训练起个名字，最多 30 个字") })

            EditorSection("01", "训练间隔") {
                PickerField("准备时间", "开始前的倒计时", formatSeconds(prepare.toLong())) { openPicker("prepare") }
                PickerField("锻炼时间", "每个周期的运动时间", formatSeconds(work.toLong())) { openPicker("work") }
                PickerField("休息时间", "相邻周期之间的休息", formatSeconds(rest.toLong())) { openPicker("rest") }
            }
            EditorSection("02", "重复次数") {
                PickerField("周期数", "每个循环锻炼多少次", "$cycles 次") { openPicker("cycles") }
                PickerField("循环数", "整组训练重复多少次", "$rounds 组") { openPicker("rounds") }
                PickerField("循环间休息", "两组训练之间的恢复时间", formatSeconds(roundRest.toLong())) { openPicker("roundRest") }
            }

            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("预计总时长", color = colors.primary, fontWeight = FontWeight.SemiBold)
                        Text(if (error == null) formatSeconds(draft.totalSeconds) else "—", color = colors.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("准备只执行一次。每组最后一轮锻炼后跳过普通休息，组间执行循环间休息。",
                        color = colors.primary, fontSize = 12.sp, lineHeight = 19.sp)
                    if (draft.rounds == 1 && draft.roundRestSeconds > 0) {
                        Text("当前只有 1 个循环，不会执行循环间休息。", color = colors.primary, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(color = colors.background) {
          Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showErrors && error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Button(onClick = {
                showErrors = true
                if (error == null && !onSave(draft)) saveError = "请先重置当前训练，再修改此计划。"
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存计划")
            }
          }
        }
    }
    if (discard) ConfirmDialog("放弃本次修改？", "尚未保存的内容将被丢弃。", "放弃修改",
        onDismiss = { discard = false }, onConfirm = { discard = false; onCancel() })
    activePicker?.let { field ->
        val dismiss = { activePicker = null }
        when (field) {
            "cycles" -> QuantityPickerDialog("周期数", cycles, "次", dismiss) { cycles = it; activePicker = null }
            "rounds" -> QuantityPickerDialog("循环数", rounds, "组", dismiss) { rounds = it; activePicker = null }
            else -> {
                val title = when (field) {
                    "prepare" -> "准备时间"
                    "work" -> "锻炼时间"
                    "rest" -> "休息时间"
                    else -> "循环间休息"
                }
                val value = when (field) {
                    "prepare" -> prepare
                    "work" -> work
                    "rest" -> rest
                    else -> roundRest
                }
                DurationPickerDialog(title, value, if (field == "work") 1 else 0, dismiss) {
                    when (field) {
                        "prepare" -> prepare = it
                        "work" -> work = it
                        "rest" -> rest = it
                        else -> roundRest = it
                    }
                    activePicker = null
                }
            }
        }
    }
}

@Composable
private fun EditorSection(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTempoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun PickerField(label: String, hint: String, value: String, onClick: () -> Unit) {
    val colors = LocalTempoColors.current
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(hint, color = colors.muted, fontSize = 11.sp)
            }
            Row(Modifier.background(colors.controlSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontSize = 20.sp) },
        text = { Text(message) }, shape = RoundedCornerShape(24.dp),
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun stageColor(stage: Stage): Color = when (stage) {
    Stage.PREPARE -> Color(0xFFF4CF80)
    Stage.WORK -> Color(0xFFB8EDB4)
    Stage.REST -> Color(0xFFA7D8EC)
    Stage.ROUND_REST -> Color(0xFFC8BDED)
}
