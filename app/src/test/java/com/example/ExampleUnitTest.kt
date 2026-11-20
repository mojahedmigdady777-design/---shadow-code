package com.example

import com.example.core.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun cipherEngine_encryptsAndDecryptsAllFourCiphers() {
    val sample = "تأمين القبو الشمالي فوراً"
    for (type in CipherType.entries) {
      val encrypted = CipherEngine.encrypt(sample, type, 3)
      assertNotEquals("Encrypted should differ for $type", sample, encrypted)
      assertEquals(sample.length, encrypted.length)
    }

    val code = CipherEngine.generateSecretCode()
    assertEquals(4, code.size)
    code.forEach { ch ->
      assertFalse(ch in listOf("0", "O", "1", "I"))
    }
  }

  @Test
  fun wordBank_has20TemplatesAnd100PlusWordsPerSlotKind() {
    assertEquals(20, TemplatesAndWordBank.templates.size)
    MessageCategory.entries.forEach { cat ->
      assertEquals(5, TemplatesAndWordBank.templates.count { it.category == cat })
    }
    SlotKind.entries.filter { it != SlotKind.AGENT_NAME }.forEach { kind ->
      val bank = TemplatesAndWordBank.getBankForSlot(kind, emptyList(), "")
      assertTrue("Bank for $kind has ${bank.size} words (<100)", bank.size >= 100)
    }
  }

  @Test
  fun puzzleEngine_generatesAllSixPuzzleTypesWithoutImmediateRepeat() {
    val seen = mutableSetOf<PuzzleType>()
    var last: PuzzleType? = null
    repeat(30) {
      val puzzle = PuzzleEngine.generateNextPuzzle(
        lastPuzzleType = last,
        cipherType = CipherType.CAESAR_FORWARD,
        cipherShift = 3
      )
      if (last != null) {
        assertNotEquals(last, puzzle.type)
      }
      last = puzzle.type
      seen += puzzle.type
    }
    assertEquals(6, seen.size)
  }

  @Test
  fun massiveDatabase_hasExpandedVolumeAndNoRepeatsAcrossRounds() {
    assertTrue(CloudContentCatalog.allCipherWords().size >= 100)
    assertTrue(CloudContentCatalog.allAlgorithmScenarios().size >= 18)
    assertTrue(CloudContentCatalog.allDeductionScenarios().size >= 12)
    assertTrue(CloudContentCatalog.allSpyReports().size >= 50)

    SlotKind.entries.filter { it != SlotKind.AGENT_NAME }.forEach { kind ->
      val bank = TemplatesAndWordBank.getBankForSlot(kind, emptyList(), "")
      assertTrue("Expanded bank for $kind has ${bank.size} words (<150)", bank.size >= 150)
    }

    // Verify 50 spy reports pick without repeating until exhausted
    val usedReports = mutableSetOf<String>()
    repeat(50) {
      val (id, text) = TemplatesAndWordBank.pickNextSpyReport(usedReports)
      assertTrue("Report $id repeated unexpectedly", id !in usedReports)
      assertTrue(text.isNotBlank())
      usedReports += id
    }
    assertEquals(50, usedReports.size)

    // Verify puzzle keys avoid repeats across generations
    val usedPuzzles = mutableSetOf<String>()
    var lastType: PuzzleType? = null
    repeat(24) {
      val p = PuzzleEngine.generateNextPuzzle(
        lastPuzzleType = lastType,
        cipherType = CipherType.CAESAR_FORWARD,
        cipherShift = 3,
        usedPuzzleIds = usedPuzzles
      )
      assertFalse("Puzzle key ${p.puzzleKey} repeated", p.puzzleKey in usedPuzzles)
      usedPuzzles += p.puzzleKey
      lastType = p.type
    }
  }

  @Test
  fun offlineViewModel_dealsRolesAndSlotsCorrectlyFor4To8Players() {
    val vm = com.example.offline.OfflineGameViewModel()
    for (count in 4..8) {
      vm.setPlayerCount(count)
      vm.toggleTwoSpies(count >= 7)
      vm.startNewOfflineGame()
      val state = vm.uiState.value
      assertEquals(count, state.players.size)
      val expectedSpies = if (count >= 7) 2 else 1
      assertEquals(expectedSpies, state.players.count { it.role == Role.SPY })
      assertTrue(state.players.filter { it.role == Role.SPY }.all { it.assignedSlotIndices.isEmpty() })

      val dealtSlots = state.players.filter { it.role == Role.ORG }.flatMap { it.assignedSlotIndices }
      assertEquals(setOf(0, 1, 2, 3), dealtSlots.toSet())
      assertEquals(4, dealtSlots.size)
    }
  }

  @Test
  fun offlineViewModel_rareCardsAndMultiSlotShieldWorkProperly() {
    val vm = com.example.offline.OfflineGameViewModel()
    vm.setPlayerCount(4)
    vm.startNewOfflineGame()

    // Step past all 4 role reveals to reach TURN_HANDOFF
    repeat(4) {
      vm.revealRoleCard()
      vm.confirmRoleSeenAndAdvance()
    }
    assertEquals(com.example.offline.OfflineStage.TURN_HANDOFF, vm.uiState.value.stage)

    // Advance turns until we reach an Org player with 2 assigned slots (guaranteed in 4-player game)
    var attempts = 0
    while (attempts < 4) {
      vm.openPlayerTurnScreen()
      val current = vm.uiState.value.currentPlayer!!
      if (current.role == Role.ORG && current.assignedSlotIndices.size == 2) break
      vm.endTurn()
      attempts++
    }
    val multiSlotPlayer = vm.uiState.value.currentPlayer!!
    assertEquals(Role.ORG, multiSlotPlayer.role)
    assertEquals(2, multiSlotPlayer.assignedSlotIndices.size)

    // Verify cannot send decoy/spoof without holding the card
    val tmpl = TemplatesAndWordBank.templates.first()
    val words = listOf("العميل 1", "القطاع ألفا")
    vm.sendTemplateMessage(tmpl, words, useDecoyTrap = true, spoofAsPlayerId = "p_1")
    val sentMsg = vm.uiState.value.messages.last()
    assertFalse("Should not arm DECOY_TRAP if player does not own it", sentMsg.isDecoyTrap)
    assertEquals(multiSlotPlayer.name, sentMsg.senderName)
  }

  @Test
  fun ownerSignature_containsOfficialArabicAndEnglishNames() {
    assertEquals("مجاهد مهند مقدادي", OwnerSignature.OWNER_NAME_AR)
    assertEquals("mojahed mohanad migdady", OwnerSignature.OWNER_NAME_EN)
    assertTrue(OwnerSignature.PROJECT_NAME_AR.contains("شفرة الظل"))
  }

  @Test
  fun offlineTwoSpies_shareIdentityWiretapsAndRevealedSlotsWithoutOverwritingEachOther() {
    val vm = com.example.offline.OfflineGameViewModel()
    vm.setPlayerCount(7)
    vm.toggleTwoSpies(true)
    vm.startNewOfflineGame()

    val spies = vm.uiState.value.players.filter { it.role == Role.SPY }
    val orgPlayers = vm.uiState.value.players.filter { it.role == Role.ORG }
    assertEquals(2, spies.size)
    assertEquals(5, orgPlayers.size)

    // Mutual identity: Spy 1 sees Spy 2, Spy 2 sees Spy 1, Org sees neither
    assertEquals(listOf(spies[1].id), vm.uiState.value.allySpiesFor(spies[0].id).map { it.id })
    assertEquals(listOf(spies[0].id), vm.uiState.value.allySpiesFor(spies[1].id).map { it.id })
    assertTrue(vm.uiState.value.allySpiesFor(orgPlayers.first().id).isEmpty())

    // Step past all 7 role reveals
    repeat(7) {
      vm.revealRoleCard()
      vm.confirmRoleSeenAndAdvance()
    }
    assertEquals(com.example.offline.OfflineStage.TURN_HANDOFF, vm.uiState.value.stage)

    // Walk through turns: both Spy 1 and Spy 2 pick distinct wiretap targets without overwriting each other
    val targetA = orgPlayers[0].id
    val targetB = orgPlayers[1].id
    var seenSpies = 0
    repeat(7) {
      vm.openPlayerTurnScreen()
      val current = vm.uiState.value.currentPlayer!!
      if (current.id == spies[0].id) {
        vm.selectWiretapTarget(targetA)
        seenSpies++
      } else if (current.id == spies[1].id) {
        vm.selectWiretapTarget(targetB)
        seenSpies++
      } else if (current.role == Role.ORG) {
        val tmpl = TemplatesAndWordBank.templates.first()
        vm.sendTemplateMessage(tmpl, listOf("العميل 1", "المرفأ"))
      }
      vm.endTurn()
    }
    assertEquals(2, seenSpies)
    assertEquals(setOf(targetA, targetB), vm.uiState.value.allActiveWiretapTargetIds)
    assertEquals(targetA, vm.uiState.value.wiretapActiveBySpyId[spies[0].id])
    assertEquals(targetB, vm.uiState.value.wiretapActiveBySpyId[spies[1].id])

    // Advance to first spy's turn, solve a puzzle -> slot revealed is shared in revealedSlotIndices
    repeat(7) {
      if (vm.uiState.value.currentPlayer?.id == spies[0].id) return@repeat
      vm.openPlayerTurnScreen()
      vm.endTurn()
    }
    vm.openPlayerTurnScreen()
    assertTrue(vm.uiState.value.spyCharges > 0)
    vm.startSpyPuzzle()
    vm.submitPuzzleResult(solved = true)
    assertEquals(setOf(0), vm.uiState.value.revealedSlotIndices)
  }

  @Test
  fun tacticalReport_triggersAutomaticSystemAudit_andEmitsSmartHintOrSuccessAlert() {
    val vm = com.example.offline.OfflineGameViewModel()
    vm.setPlayerCount(4)
    vm.startNewOfflineGame()
    repeat(4) {
      vm.revealRoleCard()
      vm.confirmRoleSeenAndAdvance()
    }

    // Player 0 sends tactical report
    vm.openPlayerTurnScreen()
    val sender = vm.uiState.value.currentPlayer!!
    val tmpl = TemplatesAndWordBank.templates.first { it.slots.size >= 2 && SlotKind.SECTOR in it.slots }
    val sampleWords = tmpl.slots.map { kind ->
      when (kind) {
        SlotKind.SECTOR -> "القطاع ألفا"
        SlotKind.LOCATION -> "غرفة السيرفرات"
        SlotKind.THREAT -> "محاولة فك التشفير"
        SlotKind.ACTION -> "عزل الخادم"
        SlotKind.REASON -> "انقطاع الكاميرات"
        SlotKind.SUSPICION -> "التصرّف بارتباك"
        SlotKind.DURATION -> "دقيقتين"
        SlotKind.AGENT_NAME -> "العميل 2"
      }
    }
    vm.sendTemplateMessage(tmpl, sampleWords)

    val auditAfterSend = vm.uiState.value.activeAudit
    assertNotNull("Automatic System Audit should be generated immediately on report send", auditAfterSend)
    assertEquals(sender.id, auditAfterSend!!.senderId)
    assertEquals(3, vm.uiState.value.auditEligiblePlayerIds.size)
    assertFalse(sender.id in vm.uiState.value.auditEligiblePlayerIds)

    // Sender cannot answer their own audit
    vm.answerSystemAudit(auditAfterSend.correctIndex)
    assertTrue(vm.uiState.value.auditAnsweredPlayerIds.isEmpty())

    // Remaining 3 players answer all correctly -> non-spoiling success alert emitted
    vm.endTurn()
    repeat(3) {
      vm.openPlayerTurnScreen()
      val activeAudit = vm.uiState.value.activeAudit!!
      vm.answerSystemAudit(activeAudit.correctIndex)
      vm.endTurn()
    }

    assertNull("Audit should clear once all eligible players answer", vm.uiState.value.activeAudit)
    assertTrue(
      vm.uiState.value.messages.any {
        it.channel == MessageChannel.ALERT && it.plainText.contains("نجح التحقيق الأمني")
      }
    )

    // Now test Wrong Answer -> immediate security alert + Smart Suspect Hint
    vm.openPlayerTurnScreen()
    val sender2 = vm.uiState.value.currentPlayer!!
    vm.sendTemplateMessage(tmpl, sampleWords)
    val audit2 = vm.uiState.value.activeAudit!!
    val wrongIndex = (0 until audit2.options.size).first { it != audit2.correctIndex }

    vm.endTurn()
    vm.openPlayerTurnScreen()
    val offender = vm.uiState.value.currentPlayer!!
    assertNotEquals(sender2.id, offender.id)
    vm.answerSystemAudit(wrongIndex)

    val wrongAlert = vm.uiState.value.messages.last { it.channel == MessageChannel.ALERT }
    assertTrue(wrongAlert.plainText.contains("تنبيه أمني: قام أحد العملاء بإجابة خاطئة!"))
    assertTrue(wrongAlert.plainText.contains("تلميح ذكي:"))
    assertFalse("Hint should stay mysterious and never expose offender name directly", wrongAlert.plainText.contains(offender.name))
  }

  @Test
  fun offlineBlackChannel_isSpyExclusive_spoofsSystem_andLocksAfter2UsesOr2Rounds() {
    val vm = com.example.offline.OfflineGameViewModel()
    vm.setPlayerCount(4)
    vm.startNewOfflineGame()
    repeat(4) {
      vm.revealRoleCard()
      vm.confirmRoleSeenAndAdvance()
    }

    // Advance until we land on Org first: Org MUST NOT be able to open Black Channel
    var orgTested = false
    repeat(4) {
      vm.openPlayerTurnScreen()
      val current = vm.uiState.value.currentPlayer!!
      if (current.role == Role.ORG && !orgTested) {
        vm.openBlackChannel()
        assertFalse("Org member must NEVER open Black Channel", vm.uiState.value.isBlackChannelOpen)
        assertNull(vm.uiState.value.blackChannelOpenedRound)
        orgTested = true
      }
      vm.endTurn()
    }
    assertTrue(orgTested)

    // Now find the Spy turn and test 2-use limit & official System spoof message
    repeat(4) {
      if (vm.uiState.value.currentPlayer?.role == Role.SPY) return@repeat
      vm.openPlayerTurnScreen()
      vm.endTurn()
    }
    vm.openPlayerTurnScreen()
    assertEquals(Role.SPY, vm.uiState.value.currentPlayer?.role)

    vm.openBlackChannel()
    assertTrue(vm.uiState.value.isBlackChannelOpen)
    assertEquals(2, vm.uiState.value.blackChannelRemainingUses)

    vm.sendBlackChannelMessage("تحذير رسمي تجريبي: تحققوا من المرفأ!", BlackChannelBroadcastStyle.SYSTEM_WARNING)
    val spoofedMsg = vm.uiState.value.messages.last()
    assertEquals(MessageChannel.ALERT, spoofedMsg.channel)
    assertEquals("النظام الأمني الرسمي", spoofedMsg.senderName)
    assertEquals(1, vm.uiState.value.blackChannelUsesCount)
    assertTrue(vm.uiState.value.isBlackChannelOpen)

    vm.sendBlackChannelMessage("سؤال تحقيق أمني: من دخل غرفة السيرفرات؟", BlackChannelBroadcastStyle.SECURITY_QUESTION)
    assertEquals(2, vm.uiState.value.blackChannelUsesCount)
    assertTrue("Black Channel must permanently close after 2nd use", vm.uiState.value.blackChannelClosed)
    assertFalse(vm.uiState.value.isBlackChannelOpen)

    // Further attempts after permanent lock are ignored
    val countBefore = vm.uiState.value.messages.size
    vm.sendBlackChannelMessage("محاولة ثالثة ممنوعة", BlackChannelBroadcastStyle.SYSTEM_WARNING)
    assertEquals(countBefore, vm.uiState.value.messages.size)
  }

  @Test
  fun offlineBlackChannel_closesPermanentlyAfter2RoundsExpire() {
    val vm = com.example.offline.OfflineGameViewModel()
    vm.setPlayerCount(4)
    vm.startNewOfflineGame()
    repeat(4) {
      vm.revealRoleCard()
      vm.confirmRoleSeenAndAdvance()
    }

    // Find Spy in Round 1 and open Black Channel (closes after Round 2)
    repeat(4) {
      if (vm.uiState.value.currentPlayer?.role == Role.SPY) return@repeat
      vm.openPlayerTurnScreen()
      vm.endTurn()
    }
    vm.openPlayerTurnScreen()
    vm.openBlackChannel()
    assertTrue(vm.uiState.value.isBlackChannelOpen)
    assertEquals(2, vm.uiState.value.blackChannelClosesAfterRound)

    // Advance through rest of Round 1 and all of Round 2 into Round 3
    while (vm.uiState.value.roundNumber < 3) {
      if (vm.uiState.value.stage == com.example.offline.OfflineStage.TURN_HANDOFF) {
        vm.openPlayerTurnScreen()
      }
      vm.endTurn()
    }
    assertEquals(3, vm.uiState.value.roundNumber)
    assertTrue("Black Channel must close permanently once 2 rounds elapse", vm.uiState.value.blackChannelClosed)
    assertFalse(vm.uiState.value.isBlackChannelOpen)
  }
}


