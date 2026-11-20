package com.example.core

import kotlin.random.Random

sealed interface ActivePuzzle {
    val type: PuzzleType
    val timeLimitSeconds: Int
    val puzzleKey: String

    data class CipherDecoding(
        val cipheredWord: String,
        val clueText: String,
        val options: List<String>,
        val correctIndex: Int,
        val eliminatedIndices: Set<Int> = emptySet(),
        override val puzzleKey: String,
        override val timeLimitSeconds: Int = 25
    ) : ActivePuzzle {
        override val type = PuzzleType.CIPHER_DECODING
    }

    data class LogicMatrix(
        val patternHint: String,
        val numbers: List<Int?>, // 9 items (3x3) or 6 items, one is null (?)
        val options: List<Int>,
        val correctIndex: Int,
        override val puzzleKey: String,
        override val timeLimitSeconds: Int = 28
    ) : ActivePuzzle {
        override val type = PuzzleType.LOGIC_MATRIX
    }

    data class DeductionGrid(
        val entities: List<String>,
        val locations: List<String>,
        val clues: List<String>,
        val solution: Map<String, String>,
        override val puzzleKey: String,
        override val timeLimitSeconds: Int = 30
    ) : ActivePuzzle {
        override val type = PuzzleType.DEDUCTION_GRID
    }

    data class MemoryFlash(
        val gridSize: Int, // 9 (3x3) or 12 (3x4)
        val sequence: List<Int>,
        val reverseOrder: Boolean,
        override val puzzleKey: String,
        override val timeLimitSeconds: Int = 24
    ) : ActivePuzzle {
        override val type = PuzzleType.MEMORY_FLASH
    }

    data class CircuitRouting(
        val rows: Int = 3,
        val cols: Int = 4,
        val tiles: List<PipeTile>, // size = rows * cols
        override val puzzleKey: String,
        override val timeLimitSeconds: Int = 30
    ) : ActivePuzzle {
        override val type = PuzzleType.CIRCUIT_ROUTING
    }

    data class AlgorithmSequence(
        val scenarioTitle: String,
        val scrambledSteps: List<String>,
        val clues: List<String>,
        val correctOrder: List<String>,
        override val puzzleKey: String,
        override val timeLimitSeconds: Int = 15
    ) : ActivePuzzle {
        override val type = PuzzleType.ALGORITHM_SEQUENCE
    }
}

enum class PipeShape {
    STRAIGHT, // base orientation 0: LEFT-RIGHT
    ELBOW,    // base orientation 0: TOP-RIGHT
    TEE       // base orientation 0: LEFT-RIGHT-TOP
}

data class PipeTile(
    val row: Int,
    val col: Int,
    val shape: PipeShape,
    val rotation: Int // 0, 1, 2, 3 (90 deg clockwise steps)
) {
    fun openPorts(): Set<Int> {
        val base = when (shape) {
            PipeShape.STRAIGHT -> setOf(1, 3)
            PipeShape.ELBOW -> setOf(0, 1)
            PipeShape.TEE -> setOf(0, 1, 3)
        }
        return base.map { (it + rotation) % 4 }.toSet()
    }
}

object PuzzleEngine {

    fun generateNextPuzzle(
        lastPuzzleType: PuzzleType?,
        cipherType: CipherType,
        cipherShift: Int,
        eliminateTwoWrongInCipher: Boolean = false,
        usedPuzzleIds: Set<String> = emptySet(),
        random: Random = Random.Default
    ): ActivePuzzle {
        val candidates = PuzzleType.entries.filter { it != lastPuzzleType }
        val chosen = candidates[random.nextInt(candidates.size)]
        return when (chosen) {
            PuzzleType.CIPHER_DECODING -> generateCipherPuzzle(cipherType, cipherShift, eliminateTwoWrongInCipher, usedPuzzleIds, random)
            PuzzleType.LOGIC_MATRIX -> generateLogicMatrix(usedPuzzleIds, random)
            PuzzleType.DEDUCTION_GRID -> generateDeductionGrid(usedPuzzleIds, random)
            PuzzleType.MEMORY_FLASH -> generateMemoryFlash(usedPuzzleIds, random)
            PuzzleType.CIRCUIT_ROUTING -> generateCircuitRouting(usedPuzzleIds, random)
            PuzzleType.ALGORITHM_SEQUENCE -> generateAlgorithmSequence(usedPuzzleIds, random)
        }
    }

    private fun generateCipherPuzzle(
        cipherType: CipherType,
        cipherShift: Int,
        eliminateTwoWrong: Boolean,
        usedPuzzleIds: Set<String>,
        random: Random
    ): ActivePuzzle.CipherDecoding {
        val allWords = CloudContentCatalog.allCipherWords()
        val unusedAnswers = allWords.filter { "CIPHER:$it" !in usedPuzzleIds }
        val answer = if (unusedAnswers.isNotEmpty()) {
            unusedAnswers.random(random)
        } else {
            allWords.random(random)
        }
        val distractors = allWords.filter { it != answer }.shuffled(random).take(3)
        val encrypted = CipherEngine.encrypt(answer, cipherType, cipherShift)
        val options = (distractors + answer).shuffled(random)
        val correctIdx = options.indexOf(answer)
        val eliminated = if (eliminateTwoWrong) {
            options.indices.filter { it != correctIdx }.shuffled(random).take(2).toSet()
        } else emptySet()
        val clue = when (cipherType) {
            CipherType.CAESAR_FORWARD -> "القاعدة النشطة: إزاحة للأمام على الأبجدية العربية"
            CipherType.CAESAR_BACKWARD -> "القاعدة النشطة: إزاحة عكسية على الأبجدية العربية"
            CipherType.REVERSE -> "القاعدة النشطة: عكس ترتيب الأحرف"
            CipherType.SWAP_PAIRS -> "القاعدة النشطة: تبديل كل حرفين متجاورين"
        }
        return ActivePuzzle.CipherDecoding(
            cipheredWord = encrypted,
            clueText = clue,
            options = options,
            correctIndex = correctIdx,
            eliminatedIndices = eliminated,
            puzzleKey = "CIPHER:$answer"
        )
    }

    private fun generateLogicMatrix(usedPuzzleIds: Set<String>, random: Random): ActivePuzzle.LogicMatrix {
        repeat(12) { attempt ->
            val candidate = buildSingleLogicMatrix(random)
            if (candidate.puzzleKey !in usedPuzzleIds || attempt == 11) {
                return candidate
            }
        }
        return buildSingleLogicMatrix(random)
    }

    private fun buildSingleLogicMatrix(random: Random): ActivePuzzle.LogicMatrix {
        val mode = random.nextInt(6)
        val seq: List<Int>
        val hint: String
        when (mode) {
            0 -> {
                val start = random.nextInt(3, 25)
                val step = random.nextInt(4, 15)
                seq = List(9) { start + it * step }
                hint = "متسلسلة حسابية ثابتة الزيادة"
            }
            1 -> {
                val start = random.nextInt(2, 6)
                val ratio = random.nextInt(2, 4)
                seq = List(6) { i ->
                    var v = start
                    repeat(i) { v *= ratio }
                    v
                }
                hint = "متسلسلة هندسية بمضاعفة ثابتة"
            }
            2 -> {
                var cur = random.nextInt(2, 14)
                val baseDiff = random.nextInt(2, 7)
                val list = mutableListOf(cur)
                for (i in 1..8) {
                    cur += baseDiff + (i - 1) * 2
                    list += cur
                }
                seq = list
                hint = "فروق متزايدة بمقدار منتظم"
            }
            3 -> {
                val offset = random.nextInt(0, 10)
                seq = List(9) { i -> (i + 2) * (i + 2) + offset }
                hint = "مربعات أعداد متتالية مع إزاحة ثابتة"
            }
            4 -> {
                val a = random.nextInt(1, 7)
                val b = random.nextInt(2, 9)
                val list = mutableListOf(a, b)
                for (i in 2..8) list += list[i - 1] + list[i - 2]
                seq = list
                hint = "كل رقم يساوي مجموع الرقمين السابقين"
            }
            else -> {
                val primes = listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47)
                val offset = random.nextInt(0, 6)
                seq = primes.subList(offset, offset + 9)
                hint = "أعداد أولية متتالية"
            }
        }

        val missingIdx = random.nextInt(seq.size - 3, seq.size)
        val answer = seq[missingIdx]
        val display = seq.mapIndexed { index, value -> if (index == missingIdx) null else value }
        val distractors = mutableSetOf<Int>()
        while (distractors.size < 3) {
            val delta = listOf(-6, -4, -3, -2, -1, 1, 2, 3, 4, 6, 8).random(random)
            val cand = answer + delta
            if (cand > 0 && cand != answer) distractors += cand
        }
        val options = (distractors + answer).shuffled(random)
        val key = "MATRIX:${mode}_${seq.first()}_${seq.last()}_$missingIdx"
        return ActivePuzzle.LogicMatrix(
            patternHint = hint,
            numbers = display,
            options = options,
            correctIndex = options.indexOf(answer),
            puzzleKey = key
        )
    }

    private fun generateDeductionGrid(usedPuzzleIds: Set<String>, random: Random): ActivePuzzle.DeductionGrid {
        val scenarios = CloudContentCatalog.allDeductionScenarios()
        val unused = scenarios.filter { "DEDUCT:${it.id}" !in usedPuzzleIds }
        val chosen = if (unused.isNotEmpty()) unused.random(random) else scenarios.random(random)

        val entities = chosen.entities
        val locations = chosen.locations
        val shuffledLocs = locations.shuffled(random)
        val solution = entities.indices.associate { entities[it] to shuffledLocs[it] }

        val clues = listOf(
            "${entities[0]} ليس في ${shuffledLocs[1]} وليس في ${shuffledLocs[2]}.",
            "${entities[1]} لا يتواجد في ${shuffledLocs[2]}.",
            "الموجود في ${shuffledLocs[2]} هو ${entities[2]}."
        ).shuffled(random)

        return ActivePuzzle.DeductionGrid(
            entities = entities,
            locations = locations,
            clues = clues,
            solution = solution,
            puzzleKey = "DEDUCT:${chosen.id}"
        )
    }

    private fun generateMemoryFlash(usedPuzzleIds: Set<String>, random: Random): ActivePuzzle.MemoryFlash {
        repeat(10) { attempt ->
            val gridSize = if (random.nextBoolean()) 9 else 12
            val seqLength = if (gridSize == 9) 4 else 5
            val seq = (0 until gridSize).shuffled(random).take(seqLength)
            val reverseOrder = random.nextInt(3) == 0
            val key = "MEM:${gridSize}_${reverseOrder}_${seq.joinToString("-")}"
            if (key !in usedPuzzleIds || attempt == 9) {
                return ActivePuzzle.MemoryFlash(
                    gridSize = gridSize,
                    sequence = seq,
                    reverseOrder = reverseOrder,
                    puzzleKey = key
                )
            }
        }
        val seq = (0 until 9).shuffled(random).take(4)
        return ActivePuzzle.MemoryFlash(9, seq, false, "MEM:9_false_${seq.joinToString("-")}")
    }

    private fun generateCircuitRouting(usedPuzzleIds: Set<String>, random: Random): ActivePuzzle.CircuitRouting {
        val rows = 3
        val cols = 4
        val allVariants = listOf(
            0 to listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1, 2 to 2, 2 to 3),
            1 to listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2, 2 to 2, 2 to 3),
            2 to listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2, 2 to 3),
            3 to listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2, 2 to 3),
            4 to listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 1 to 3, 2 to 3),
            5 to listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2, 1 to 3, 2 to 3),
            6 to listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2, 2 to 3),
            7 to listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2, 1 to 3, 2 to 3),
            8 to listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2, 2 to 2, 2 to 3),
            9 to listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 1 to 3, 2 to 3),
            10 to listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 1 to 1, 0 to 1, 0 to 2, 0 to 3, 1 to 3, 2 to 3),
            11 to listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 1 to 3, 1 to 2, 2 to 2, 2 to 3),
            12 to listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2, 1 to 2, 1 to 3, 2 to 3),
            13 to listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 1 to 1, 1 to 2, 2 to 2, 2 to 3),
            14 to listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1, 2 to 2, 1 to 2, 1 to 3, 2 to 3),
            15 to listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 1 to 1, 2 to 1, 2 to 2, 2 to 3)
        )
        val unusedVariants = allVariants.filter { "CIRCUIT:V${it.first}" !in usedPuzzleIds }
        val (variantId, path) = if (unusedVariants.isNotEmpty()) {
            unusedVariants.random(random)
        } else {
            allVariants.random(random)
        }

        val tiles = MutableList(rows * cols) { idx ->
            val r = idx / cols
            val c = idx % cols
            val shape = PipeShape.entries[random.nextInt(PipeShape.entries.size)]
            PipeTile(r, c, shape, random.nextInt(4))
        }

        for (i in path.indices) {
            val (r, c) = path[i]
            val prevDir = if (i == 0) 3 else directionBetween(path[i] to path[i - 1])
            val nextDir = if (i == path.lastIndex) 1 else directionBetween(path[i] to path[i + 1])
            val isOpposite = (prevDir + 2) % 4 == nextDir
            val shape = if (isOpposite) PipeShape.STRAIGHT else PipeShape.ELBOW
            val solvedRot = findSolvedRotation(shape, setOf(prevDir, nextDir))
            val scrambledRot = (solvedRot + random.nextInt(1, 4)) % 4
            tiles[r * cols + c] = PipeTile(r, c, shape, scrambledRot)
        }

        return ActivePuzzle.CircuitRouting(
            rows = rows,
            cols = cols,
            tiles = tiles,
            puzzleKey = "CIRCUIT:V$variantId"
        )
    }

    private fun directionBetween(pair: Pair<Pair<Int, Int>, Pair<Int, Int>>): Int {
        val (from, to) = pair
        return when {
            to.first < from.first -> 0
            to.second > from.second -> 1
            to.first > from.first -> 2
            else -> 3
        }
    }

    private fun findSolvedRotation(shape: PipeShape, needed: Set<Int>): Int {
        for (rot in 0..3) {
            val tile = PipeTile(0, 0, shape, rot)
            if (tile.openPorts().containsAll(needed)) return rot
        }
        return 0
    }

    fun computeConnectedCircuitCells(rows: Int, cols: Int, tiles: List<PipeTile>): Pair<Set<Int>, Boolean> {
        val startTile = tiles[0]
        if (3 !in startTile.openPorts()) return emptySet<Int>() to false

        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()
        visited += 0
        queue += 0

        val dr = intArrayOf(-1, 0, 1, 0)
        val dc = intArrayOf(0, 1, 0, -1)

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val r = idx / cols
            val c = idx % cols
            val tile = tiles[idx]
            for (dir in tile.openPorts()) {
                val nr = r + dr[dir]
                val nc = c + dc[dir]
                if (nr in 0 until rows && nc in 0 until cols) {
                    val nIdx = nr * cols + nc
                    val oppDir = (dir + 2) % 4
                    if (nIdx !in visited && oppDir in tiles[nIdx].openPorts()) {
                        visited += nIdx
                        queue += nIdx
                    }
                }
            }
        }
        val targetIdx = (rows - 1) * cols + (cols - 1)
        val reachedTarget = targetIdx in visited && 1 in tiles[targetIdx].openPorts()
        return visited to reachedTarget
    }

    private fun generateAlgorithmSequence(usedPuzzleIds: Set<String>, random: Random): ActivePuzzle.AlgorithmSequence {
        val scenarios = CloudContentCatalog.allAlgorithmScenarios()
        val unused = scenarios.filter { "ALGO:${it.id}" !in usedPuzzleIds }
        val chosen = if (unused.isNotEmpty()) unused.random(random) else scenarios.random(random)
        return ActivePuzzle.AlgorithmSequence(
            scenarioTitle = chosen.title,
            scrambledSteps = chosen.steps.shuffled(random),
            clues = chosen.clues,
            correctOrder = chosen.steps,
            puzzleKey = "ALGO:${chosen.id}"
        )
    }
}
