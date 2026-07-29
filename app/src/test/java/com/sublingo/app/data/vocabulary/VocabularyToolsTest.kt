package com.sublingo.app.data.vocabulary

import com.sublingo.app.data.db.SubtitleCueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class VocabularyToolsTest {
    @Test fun `resolver highlights source term retained in Chinese subtitle`() {
        assertEquals(
            "Siri",
            ContextualChineseMeaningResolver.resolve(
                contextZh = "现在关于这个新Siri的事情是。",
                alignedMeaningZh = null,
                definitionZh = null,
                sourceTerms = listOf("Siri"),
            ),
        )
    }

    @Test fun `resolver reuses only a validated translation present in the current cue`() {
        assertEquals(
            "应用程序",
            ContextualChineseMeaningResolver.resolve(
                contextZh = "这个应用程序保留了历史记录。",
                alignedMeaningZh = null,
                definitionZh = null,
                alignedCandidatesZh = listOf("应用", "应用程序"),
            ),
        )
    }
    @Test fun contextualChineseMeaningPrefersAlignmentThenExactDictionaryTerm() {
        assertEquals(
            "苹果公司",
            ContextualChineseMeaningResolver.resolve("苹果公司刚刚发布了产品。", "苹果公司", "n. 苹果, 水果"),
        )
        assertEquals(
            "苹果",
            ContextualChineseMeaningResolver.resolve("苹果智能目前表现一般。", null, "n. 苹果, 家伙；[医] 苹果"),
        )
    }

    @Test fun contextualChineseMeaningDoesNotGuessWhenDefinitionIsAbsentFromSubtitle() {
        assertEquals(
            null,
            ContextualChineseMeaningResolver.resolve("一切都从零开始。", null, "a. 离去的, 死去的, 用完的"),
        )
    }

    @Test fun xiaomiUsesSmallBatchesAndBoundedRetries() {
        val policy = VocabularyLlmPolicy.forPreset("xiaomi-mimo")

        assertEquals(24, policy.cuesPerBatch)
        assertEquals(16, policy.maxRequestsPerRun)
        assertEquals(2, policy.maxSplitDepth)
        assertEquals(2, policy.maxAttemptsPerInput)
        assertFalse(policy.remoteExtractionEnabled)
        val batches = policy.batches((0 until 312).toList())
        assertEquals(13, batches.size)
        assertTrue(batches.all { it.size <= 24 })
    }

    @Test fun nonXiaomiProvidersKeepRemoteVocabularyExtraction() {
        assertTrue(VocabularyLlmPolicy.forPreset("deepseek").remoteExtractionEnabled)
        assertTrue(VocabularyLlmPolicy.forPreset("custom").remoteExtractionEnabled)
    }

    @Test fun requestBudgetNeverExceedsItsLimit() {
        val budget = VocabularyRequestBudget(2)

        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
        assertEquals(2, budget.used)
        budget.disable()
        assertFalse(budget.tryAcquire())
        assertTrue(budget.isDisabled)
    }

    @Test fun phraseAuditPlannerKeepsHighValueChunksAndSkipsCoveredCues() {
        val cues = listOf(
            SubtitleCueEntity("a", "track", 0, 0, 1, "Thank you for coming today"),
            SubtitleCueEntity("b", "track", 1, 1, 2, "By the way I do appreciate that"),
            SubtitleCueEntity("c", "track", 2, 2, 3, "Hello there"),
        )

        val planned = PhraseAuditPlanner.candidates(cues, alreadyCoveredCueIds = setOf("a"))

        assertEquals(listOf("a", "b"), planned.map { it.id })
    }

    @Test fun phraseAuditUsesSmallPredictableBatches() {
        assertTrue(PhraseAuditPlanner.BATCH_SIZE in 16..32)
    }
    @Test fun preprocessingDeduplicatesAndKeepsFrequency() {
        val cues = listOf(
            SubtitleCueEntity("c1", "t", 0, 0, 1_000, "Developers are developing reliable applications."),
            SubtitleCueEntity("c2", "t", 1, 1_000, 2_000, "Reliable applications help developers."),
        )
        val candidates = VocabularyPreprocessor.extract(cues)
        assertEquals(1, candidates.count { it.normalized == "reliable" })
        assertEquals(2, candidates.first { it.normalized == "reliable" }.frequency)
    }

    @Test fun singularWordsEndingInSAreNotTruncated() {
        assertEquals("consensus", VocabularyPreprocessor.normalize("consensus"))
        assertEquals("analysis", VocabularyPreprocessor.normalize("analysis"))
        assertEquals("status", VocabularyPreprocessor.normalize("status"))
        assertEquals("news", VocabularyPreprocessor.normalize("news"))
    }

    @Test fun silentEAndRegularInflectionsOfferCorrectDictionaryLemmas() {
        assertEquals("eliminate", VocabularyPreprocessor.normalize("eliminated"))
        assertEquals("eliminate", VocabularyLemmaRepairPolicy.dictionaryLemmaCandidates("eliminated").first())
        assertTrue("write" in VocabularyLemmaRepairPolicy.dictionaryLemmaCandidates("writing"))
        assertTrue("run" in VocabularyLemmaRepairPolicy.dictionaryLemmaCandidates("running"))
        assertTrue("work" in VocabularyLemmaRepairPolicy.dictionaryLemmaCandidates("worked"))
        assertTrue("study" in VocabularyLemmaRepairPolicy.dictionaryLemmaCandidates("studied"))
    }

    @Test fun legacyEdCorruptionOffersDictionaryValidatedRepairCandidate() {
        assertEquals(
            "eliminate",
            VocabularyLemmaRepairPolicy.correctionCandidates("eliminat", listOf("eliminated")).first(),
        )
        assertEquals(
            listOf("subscribe"),
            VocabularyLemmaRepairPolicy.correctionCandidates("subscrib", listOf("subscribed")),
        )
        assertEquals(
            listOf("write"),
            VocabularyLemmaRepairPolicy.correctionCandidates("writ", listOf("writing")),
        )
        assertTrue(VocabularyLemmaRepairPolicy.correctionCandidates("application", listOf("applications")).isEmpty())
    }

    @Test fun existingLexemeIdentityWinsOverIdDerivedFromRepairedLemma() {
        assertEquals(
            "lexeme-en-legacy-eliminat",
            VocabularyLexemeIdentity.resolve("eliminate", "lexeme-en-legacy-eliminat"),
        )
        assertEquals(
            "lexeme-en-${"eliminate".hashCode().toUInt().toString(16)}",
            VocabularyLexemeIdentity.resolve("eliminate", existingId = null),
        )
    }

    @Test fun legacyTrailingSCorruptionCanBeRepairedFromThePersistedSurface() {
        assertEquals(
            "consensus",
            VocabularyLemmaRepairPolicy.correctedLegacyLemma("consensu", listOf("consensus")),
        )
        assertEquals(null, VocabularyLemmaRepairPolicy.correctedLegacyLemma("application", listOf("applications")))
    }

    @Test fun selectionCreatesOneLemmaFromDuplicateSuggestions() {
        val candidates = listOf(VocabularyCandidate("applications", "application", "c1", 2))
        val result = VocabularySelection.sanitize(
            listOf(
                SelectedVocabulary("applications", "application", "c1"),
                SelectedVocabulary("application", "applications", "c1"),
            ),
            candidates,
        )
        assertEquals(1, result.size)
        assertEquals("application", result.single().lemma)
    }

    @Test fun selectionAcceptsCorrectLemmaWhenLocalStemDiffers() {
        val candidates = listOf(VocabularyCandidate("writing", "writ", "c1", 1))
        val result = VocabularySelection.sanitize(
            listOf(SelectedVocabulary("writing", "write", "c1")),
            candidates,
            englishByCueId = mapOf("c1" to "She is writing clearly."),
        )

        assertEquals("write", result.single().lemma)
    }

    @Test fun invalidCueAndUnknownCandidateAreRejected() {
        val candidates = listOf(VocabularyCandidate("reliable", "reliable", "c1", 1))
        val result = VocabularySelection.sanitize(
            listOf(
                SelectedVocabulary("invented", "invented", "missing"),
                SelectedVocabulary("reliable", "reliable", "missing"),
            ),
            candidates,
        )
        assertEquals(1, result.size)
        assertEquals("c1", result.single().sourceCueId)
        assertTrue(result.none { it.lemma == "invented" })
    }

    @Test fun phraseCanKeepPrepositionWhenItContainsARealCandidate() {
        val candidates = listOf(VocabularyCandidate("depends", "depend", "c1", 1))
        val result = VocabularySelection.sanitize(
            listOf(SelectedVocabulary("depends on", "depend on", "c1", "取决于", itemType = VocabularyItemType.PHRASAL_VERB)),
            candidates,
        )
        assertEquals("depend on", result.single().lemma)
        assertEquals("取决于", result.single().translationZh)
    }

    @Test fun strictValidationRejectsWrongCueAndNonVerbatimSurface() {
        val candidates = listOf(VocabularyCandidate("time", "time", "c1", 1))
        val result = VocabularySelection.sanitize(
            listOf(
                SelectedVocabulary("lot of time", "lot of time", "missing", "经常", itemType = VocabularyItemType.COLLOCATION),
                SelectedVocabulary("lots of time", "lot of time", "c1", "经常", itemType = VocabularyItemType.COLLOCATION),
            ),
            candidates,
            setOf("c1"),
            mapOf("c1" to "You a lot of time see headlines."),
            mapOf("c1" to "你经常看到标题。"),
        )
        assertTrue(result.isEmpty())
    }

    @Test fun typedPhraseSuppressesOverlappingConstituentWordsBeforePersistence() {
        val candidates = listOf(
            VocabularyCandidate("lot", "lot", "c1", 1),
            VocabularyCandidate("time", "time", "c1", 1),
        )
        val result = VocabularySelection.sanitize(
            listOf(
                SelectedVocabulary("lot", "lot", "c1", "经常"),
                SelectedVocabulary("time", "time", "c1"),
                SelectedVocabulary("lot of time", "lot of time", "c1", "经常", itemType = VocabularyItemType.COLLOCATION),
            ),
            candidates,
            setOf("c1"),
            mapOf("c1" to "You a lot of time see headlines."),
            mapOf("c1" to "你经常看到标题。"),
        )
        assertEquals(listOf("lot of time"), result.map { it.surfaceForm })
        assertEquals(VocabularyItemType.COLLOCATION, result.single().itemType)
    }

    @Test fun semanticPhraseTypesKeepCompleteConversationalChunksAndSuppressFragments() {
        val candidates = listOf(
            VocabularyCandidate("Thank", "thank", "c1", 1),
            VocabularyCandidate("coming", "com", "c1", 1),
            VocabularyCandidate("way", "way", "c2", 1),
            VocabularyCandidate("appreciate", "appreciate", "c3", 1),
        )
        val result = VocabularySelection.sanitize(
            listOf(
                SelectedVocabulary("Thank", "thank", "c1", "谢谢"),
                SelectedVocabulary("coming", "com", "c1", "到来"),
                SelectedVocabulary(
                    "Thank you for coming",
                    "thank you for coming",
                    "c1",
                    "谢谢你的到来",
                    itemType = VocabularyItemType.FORMULAIC_EXPRESSION,
                ),
                SelectedVocabulary("way", "way", "c2", "方式"),
                SelectedVocabulary(
                    "by the way",
                    "by the way",
                    "c2",
                    "顺便说一下",
                    itemType = VocabularyItemType.DISCOURSE_MARKER,
                ),
                SelectedVocabulary("appreciate", "appreciate", "c3", "感谢"),
                SelectedVocabulary(
                    "do appreciate",
                    "do appreciate",
                    "c3",
                    "确实很感谢",
                    itemType = VocabularyItemType.GRAMMATICAL_CHUNK,
                ),
            ),
            candidates,
            setOf("c1", "c2", "c3"),
            mapOf(
                "c1" to "Thank you for coming today.",
                "c2" to "By the way, this is useful.",
                "c3" to "I do appreciate your help.",
            ),
            mapOf(
                "c1" to "谢谢你的到来。",
                "c2" to "顺便说一下，这很有用。",
                "c3" to "我确实很感谢你的帮助。",
            ),
        )

        assertEquals(
            setOf("Thank you for coming", "by the way", "do appreciate"),
            result.map { it.surfaceForm }.toSet(),
        )
    }

    @Test fun sameLemmaShortAndLongVariantsReachOverlapResolverSoLongPhraseWins() {
        val candidates = listOf(VocabularyCandidate("coming", "com", "c1", 1))
        val result = VocabularySelection.sanitize(
            listOf(
                SelectedVocabulary(
                    "Thank you",
                    "thank you",
                    "c1",
                    "谢谢",
                    itemType = VocabularyItemType.FORMULAIC_EXPRESSION,
                ),
                SelectedVocabulary(
                    "Thank you for coming",
                    "thank you",
                    "c1",
                    "谢谢你的到来",
                    itemType = VocabularyItemType.FORMULAIC_EXPRESSION,
                ),
            ),
            candidates,
            setOf("c1"),
            mapOf("c1" to "Thank you for coming."),
            mapOf("c1" to "谢谢你的到来。"),
        )

        assertEquals(listOf("Thank you for coming"), result.map { it.surfaceForm })
    }

    @Test fun llmJsonParserAcceptsJsonLinesWithoutOuterArray() {
        val parsed = LlmJsonResponseParser.array(
            """
            Here is the result:
            {"surfaceForm":"by the way","lemma":"by the way","itemType":"DISCOURSE_MARKER","sourceCueId":"c1"}
            {"surfaceForm":"do appreciate","lemma":"do appreciate","itemType":"GRAMMATICAL_CHUNK","sourceCueId":"c2"}
            """.trimIndent(),
            "test",
        )

        assertEquals(2, parsed.size)
        assertEquals("by the way", parsed[0].jsonObject["surfaceForm"]?.jsonPrimitive?.content)
    }

    @Test fun llmJsonParserAcceptsFencedAndWrappedArrays() {
        val parsed = LlmJsonResponseParser.array(
            """```json
            {"items":[{"surfaceForm":"Thank you for coming"}]}
            ```""",
            "test",
        )

        assertEquals("Thank you for coming", parsed.single().jsonObject["surfaceForm"]?.jsonPrimitive?.content)
    }

    @Test fun cueBatchDefinitionAndExactTranslationSurviveValidation() {
        val candidates = listOf(VocabularyCandidate("resilience", "resilience", "c1", 1))
        val result = VocabularySelection.sanitize(
            listOf(SelectedVocabulary("resilience", "resilience", "c1", "韧性", "韧性；恢复能力")),
            candidates,
        ).single()
        assertEquals("韧性", result.translationZh)
        assertEquals("韧性；恢复能力", result.definitionZh)
    }

    @Test fun sameLemmaKeepsIndependentAlignmentForEveryCue() {
        val candidates = listOf(VocabularyCandidate("bank", "bank", "c1", 2))
        val result = VocabularySelection.sanitize(
            listOf(
                SelectedVocabulary("bank", "bank", "c1", "银行"),
                SelectedVocabulary("bank", "bank", "c2", "岸边"),
            ),
            candidates,
            setOf("c1", "c2"),
        )
        assertEquals(2, result.size)
        assertEquals(setOf("银行", "岸边"), result.mapNotNull { it.translationZh }.toSet())
    }

    @Test fun preprocessingAndSanitizingDoNotApplyVocabularyCountCaps() {
        val cues = (0 until 120).map { index ->
            val suffix = "${('a'.code + index / 26).toChar()}${('a'.code + index % 26).toChar()}"
            SubtitleCueEntity("c$index", "t", index, index * 1000L, (index + 1) * 1000L, "Academicword$suffix appears here.")
        }
        val candidates = VocabularyPreprocessor.extract(cues)
        val selected = candidates.map { SelectedVocabulary(it.surfaceForm, it.normalized, it.cueId) }
        assertTrue(candidates.size >= 120)
        assertEquals(candidates.size, VocabularySelection.sanitize(selected, candidates).size)
    }
}
