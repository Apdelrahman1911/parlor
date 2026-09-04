package com.parlor.games.whodunit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.content.repository.CaseRepository
import com.parlor.content.repository.CaseUpdate
import com.parlor.content.schema.CaseSummary
import com.parlor.content.schema.IntRangePair
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ui.screens.setup.WhodunitCasePickerScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WhodunitCasePickerScreenTest {
    @Test
    fun language_filter_without_matching_cases_explains_the_empty_result() =
        runComposeUiTest {
            setContent {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(reducedMotion = true) {
                        WhodunitCasePickerScreen(
                            repository = EnglishOnlyCaseRepository,
                            onCasePicked = {},
                            onBack = {},
                        )
                    }
                }
            }

            onNodeWithText(ENGLISH_CASE_TITLE).assertExists()
            onNodeWithText(ARABIC_FILTER_LABEL).performClick()

            onNodeWithText(FILTER_EMPTY_MESSAGE).assertExists()
            onNodeWithText(ARABIC_FILTER_LABEL).assertExists()
            onNodeWithText(ENGLISH_CASE_TITLE).assertDoesNotExist()
            onNodeWithText(CONTINUE_LABEL).assertDoesNotExist()
        }

    private object EnglishOnlyCaseRepository : CaseRepository {
        override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, DataError> =
            Result.Success(
                listOf(
                    CaseSummary(
                        caseId = "english-only",
                        title = ENGLISH_CASE_TITLE,
                        version = SemVer(1, 0, 0),
                        gameId = WhodunitIds.GameId.raw,
                        supportedPlayerCounts = IntRangePair(4, 8),
                        supportedModes = listOf(WhodunitIds.ClassicVoteModeId.raw),
                        language = "en",
                        theme = "test",
                        estimatedDuration = IntRangePair(20, 30),
                        minimumAppVersion = SemVer(1, 0, 0),
                    ),
                ),
            )

        override suspend fun <TPayload> loadCase(
            id: CaseId,
            payloadValidator: PayloadValidator<TPayload>,
        ): Result<ValidatedCase<TPayload>, DataError> = Result.Failure(DataError.NotFound)

        override fun observeCacheUpdates(): Flow<CaseUpdate> = emptyFlow()

        override suspend fun <TPayload> refresh(
            gameId: GameId,
            payloadValidator: PayloadValidator<TPayload>,
        ): Result<Unit, DataError> = Result.Success(Unit)
    }

    private companion object {
        const val ENGLISH_CASE_TITLE = "English-only story"
        const val ARABIC_FILTER_LABEL = "العربية"
        const val FILTER_EMPTY_MESSAGE =
            "No stories are available in this language. Choose another filter."
        const val CONTINUE_LABEL = "Continue with this story"
    }
}
