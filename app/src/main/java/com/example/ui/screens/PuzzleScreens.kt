package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.*
import com.example.ui.components.TacticalBackground
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PuzzleHostScreen(
    puzzle: ActivePuzzle,
    secondsLeft: Int?, // null in turn-based offline mode (except visual memory flash)
    onPuzzleCompleted: (Boolean) -> Unit
) {
    val bannerRes = when (puzzle.type) {
        PuzzleType.CIPHER_DECODING -> R.drawable.img_puzzle_cipher
        PuzzleType.LOGIC_MATRIX -> R.drawable.img_puzzle_matrix
        PuzzleType.DEDUCTION_GRID -> R.drawable.img_puzzle_deduction
        PuzzleType.MEMORY_FLASH -> R.drawable.img_puzzle_memory
        PuzzleType.CIRCUIT_ROUTING -> R.drawable.img_puzzle_circuit
        PuzzleType.ALGORITHM_SEQUENCE -> R.drawable.img_puzzle_algorithm
    }

    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(puzzle.type.arabicTitle, style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                    Text(puzzle.type.englishTitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                if (secondsLeft != null) {
                    Surface(
                        color = if (secondsLeft <= 7) SpyCrimson.copy(alpha = 0.25f) else PanelSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (secondsLeft <= 7) SpyCrimson else ElectricCyan
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "الوقت المتبقي",
                                tint = if (secondsLeft <= 7) SpyCrimson else ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${secondsLeft}s",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (secondsLeft <= 7) SpyCrimson else TextPrimary
                            )
                        }
                    }
                }
            }

            Image(
                painter = painterResource(id = bannerRes),
                contentDescription = puzzle.type.arabicTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderGlow, RoundedCornerShape(16.dp))
            )

            when (puzzle) {
                is ActivePuzzle.CipherDecoding -> CipherDecodingContent(puzzle, onPuzzleCompleted)
                is ActivePuzzle.LogicMatrix -> LogicMatrixContent(puzzle, onPuzzleCompleted)
                is ActivePuzzle.DeductionGrid -> DeductionGridContent(puzzle, onPuzzleCompleted)
                is ActivePuzzle.MemoryFlash -> MemoryFlashContent(puzzle, onPuzzleCompleted)
                is ActivePuzzle.CircuitRouting -> CircuitRoutingContent(puzzle, onPuzzleCompleted)
                is ActivePuzzle.AlgorithmSequence -> AlgorithmSequenceContent(puzzle, onPuzzleCompleted)
            }
        }
    }
}

@Composable
private fun CipherDecodingContent(
    puzzle: ActivePuzzle.CipherDecoding,
    onResult: (Boolean) -> Unit
) {
    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(puzzle.clueText, style = MaterialTheme.typography.bodyMedium, color = AmberAlert, textAlign = TextAlign.Center)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MidnightOps)
                    .border(1.5.dp, ElectricCyan, RoundedCornerShape(14.dp))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = puzzle.cipheredWord,
                    style = MaterialTheme.typography.displayLarge,
                    color = ElectricCyan
                )
            }
            Text("اختر الكلمة الأصلية قبل التشفير:", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

            puzzle.options.forEachIndexed { idx, option ->
                val eliminated = idx in puzzle.eliminatedIndices
                Button(
                    onClick = { onResult(idx == puzzle.correctIndex) },
                    enabled = !eliminated,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedCard,
                        contentColor = TextPrimary,
                        disabledContainerColor = MidnightOps.copy(alpha = 0.5f),
                        disabledContentColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("cipher_option_$idx")
                ) {
                    Text(
                        text = if (eliminated) "$option (مستبعد بالتلميح)" else option,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LogicMatrixContent(
    puzzle: ActivePuzzle.LogicMatrix,
    onResult: (Boolean) -> Unit
) {
    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(puzzle.patternHint, style = MaterialTheme.typography.bodyMedium, color = AmberAlert)

            val cols = 3
            val rows = (puzzle.numbers.size + cols - 1) / cols
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until cols) {
                            val idx = r * cols + c
                            if (idx < puzzle.numbers.size) {
                                val num = puzzle.numbers[idx]
                                Box(
                                    modifier = Modifier
                                        .size(width = 84.dp, height = 56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (num == null) SpyCrimson.copy(alpha = 0.2f) else MidnightOps)
                                        .border(
                                            1.5.dp,
                                            if (num == null) SpyCrimson else BorderGlow,
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = num?.toString() ?: "?",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = if (num == null) SpyCrimson else ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text("اختر الرقم المفقود:", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                puzzle.options.forEachIndexed { idx, value ->
                    Button(
                        onClick = { onResult(idx == puzzle.correctIndex) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElevatedCard, contentColor = TextPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("matrix_option_$idx")
                    ) {
                        Text(value.toString(), fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeductionGridContent(
    puzzle: ActivePuzzle.DeductionGrid,
    onResult: (Boolean) -> Unit
) {
    val assignments = remember(puzzle) { mutableStateMapOf<String, String>() }

    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("المعطيات المنطقية:", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
            puzzle.clues.forEach { clue ->
                Text("• $clue", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            HorizontalDivider(color = BorderGlow)
            Text("وزّع كل عنصر على موقعه الصحيح:", style = MaterialTheme.typography.titleMedium, color = AmberAlert)

            puzzle.entities.forEach { entity ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entity, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        puzzle.locations.forEach { loc ->
                            val selected = assignments[entity] == loc
                            FilterChip(
                                selected = selected,
                                onClick = { assignments[entity] = loc },
                                label = { Text(loc, style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.weight(1f).testTag("deduction_${entity}_$loc")
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val allCorrect = puzzle.entities.all { assignments[it] == puzzle.solution[it] }
                    onResult(allCorrect)
                },
                enabled = assignments.size == puzzle.entities.size,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("deduction_submit_button")
            ) {
                Text("تأكيد الاستنتاج", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun MemoryFlashContent(
    puzzle: ActivePuzzle.MemoryFlash,
    onResult: (Boolean) -> Unit
) {
    var phase by remember(puzzle) { mutableStateOf("FLASH") } // FLASH -> RECALL
    var highlightedCell by remember(puzzle) { mutableStateOf<Int?>(null) }
    val userTaps = remember(puzzle) { mutableStateListOf<Int>() }

    LaunchedEffect(puzzle) {
        phase = "FLASH"
        userTaps.clear()
        delay(450L)
        for (cell in puzzle.sequence) {
            highlightedCell = cell
            delay(550L)
            highlightedCell = null
            delay(180L)
        }
        phase = "RECALL"
    }

    val expectedSequence = remember(puzzle) {
        if (puzzle.reverseOrder) puzzle.sequence.reversed() else puzzle.sequence
    }

    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val instruction = when {
                phase == "FLASH" -> "راقب تسلسل المربعات المضيئة..."
                puzzle.reverseOrder -> "أعد الضغط بالترتيب العكسي! (${userTaps.size}/${expectedSequence.size})"
                else -> "أعد الضغط بنفس الترتيب! (${userTaps.size}/${expectedSequence.size})"
            }
            Text(
                text = instruction,
                style = MaterialTheme.typography.titleMedium,
                color = if (puzzle.reverseOrder) AmberAlert else ElectricCyan,
                textAlign = TextAlign.Center
            )

            val cols = 3
            val rows = puzzle.gridSize / cols
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until cols) {
                            val idx = r * cols + c
                            val isLit = highlightedCell == idx || (phase == "RECALL" && idx in userTaps)
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isLit) ElectricCyan else MidnightOps)
                                    .border(
                                        2.dp,
                                        if (isLit) Color.White else BorderGlow,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable(enabled = phase == "RECALL" && idx !in userTaps) {
                                        userTaps.add(idx)
                                        val stepIdx = userTaps.lastIndex
                                        if (expectedSequence[stepIdx] != idx) {
                                            onResult(false)
                                        } else if (userTaps.size == expectedSequence.size) {
                                            onResult(true)
                                        }
                                    }
                                    .testTag("memory_cell_$idx"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (phase == "RECALL" && idx in userTaps) {
                                    Text(
                                        text = "${userTaps.indexOf(idx) + 1}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MidnightOps,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircuitRoutingContent(
    puzzle: ActivePuzzle.CircuitRouting,
    onResult: (Boolean) -> Unit
) {
    var tiles by remember(puzzle) { mutableStateOf(puzzle.tiles) }
    val (connectedSet, solved) = remember(tiles) {
        PuzzleEngine.computeConnectedCircuitCells(puzzle.rows, puzzle.cols, tiles)
    }

    LaunchedEffect(solved) {
        if (solved) {
            delay(350L)
            onResult(true)
        }
    }

    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("المصدر (أعلى يسار) ←", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                Text("→ الهدف (أسفل يمين)", style = MaterialTheme.typography.bodyMedium, color = EmeraldSuccess)
            }
            Text("اضغط على أي وصلة لتدويرها حتى يضيء المسار المتصل:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until puzzle.rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until puzzle.cols) {
                            val idx = r * puzzle.cols + c
                            val tile = tiles[idx]
                            val isPowered = idx in connectedSet
                            val isStart = idx == 0
                            val isEnd = idx == puzzle.rows * puzzle.cols - 1

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isPowered) Color(0xFF0A2A38) else MidnightOps)
                                    .border(
                                        1.5.dp,
                                        when {
                                            isEnd && solved -> EmeraldSuccess
                                            isPowered -> ElectricCyan
                                            isStart -> AmberAlert
                                            else -> BorderGlow
                                        },
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        val mutable = tiles.toMutableList()
                                        mutable[idx] = tile.copy(rotation = (tile.rotation + 1) % 4)
                                        tiles = mutable
                                    }
                                    .testTag("circuit_tile_$idx")
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val center = Offset(cx, cy)
                                    val pipeColor = if (isPowered) ElectricCyan else TextMuted
                                    val stroke = 9.dp.toPx()

                                    for (dir in tile.openPorts()) {
                                        val end = when (dir) {
                                            0 -> Offset(cx, 0f)
                                            1 -> Offset(size.width, cy)
                                            2 -> Offset(cx, size.height)
                                            else -> Offset(0f, cy)
                                        }
                                        drawLine(pipeColor, center, end, strokeWidth = stroke, cap = StrokeCap.Round)
                                    }
                                    drawCircle(pipeColor, radius = stroke / 1.7f, center = center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlgorithmSequenceContent(
    puzzle: ActivePuzzle.AlgorithmSequence,
    onResult: (Boolean) -> Unit
) {
    val selectedOrder = remember(puzzle) { mutableStateListOf<String>() }

    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(puzzle.scenarioTitle, style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
            puzzle.clues.forEach { clue ->
                Text("• $clue", style = MaterialTheme.typography.bodyMedium, color = AmberAlert)
            }
            HorizontalDivider(color = BorderGlow)
            Text("اضغط الأوامر بالترتيب الصحيح (1 إلى 4):", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

            puzzle.scrambledSteps.forEachIndexed { idx, step ->
                val orderNum = selectedOrder.indexOf(step) + 1
                val isSelected = orderNum > 0
                Surface(
                    color = if (isSelected) Color(0xFF0A2A4A) else ElevatedCard,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ElectricCyan else BorderGlow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) selectedOrder.remove(step)
                            else selectedOrder.add(step)
                        }
                        .testTag("algo_step_$idx")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(step, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        if (isSelected) {
                            Badge(containerColor = ElectricCyan, contentColor = MidnightOps) {
                                Text("$orderNum", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { selectedOrder.clear() },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة")
                }
                Button(
                    onClick = { onResult(selectedOrder.toList() == puzzle.correctOrder) },
                    enabled = selectedOrder.size == puzzle.correctOrder.size,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.weight(2f).height(48.dp).testTag("algo_submit_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تنفيذ الخوارزمية")
                }
            }
        }
    }
}
