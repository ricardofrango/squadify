package com.sportssoftw.squadcall.coach.presentation.ui.join

import com.sportssoftw.squadcall.presentation.ui.components.ScreenWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportssoftw.squadcall.coach.domain.JoinCode
import com.sportssoftw.squadcall.presentation.ui.components.FormErrorLine
import com.sportssoftw.squadcall.presentation.ui.components.FormIntro
import com.sportssoftw.squadcall.presentation.ui.components.FormTextField
import com.sportssoftw.squadcall.presentation.ui.components.PinnedBottomBar
import com.sportssoftw.squadcall.presentation.ui.components.PrimaryLoadingButton
import com.sportssoftw.squadcall.presentation.ui.components.SquadcallTopBar
import com.sportssoftw.squadcall.presentation.ui.components.readableWidth
import com.sportssoftw.squadcall.presentation.ui.theme.ScreenBackground
import com.sportssoftw.squadcall.presentation.ui.theme.SquadcallTheme
import com.sportssoftw.squadcall.presentation.ui.theme.screenContentPadding
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadcall.app.coach.generated.resources.Res
import squadcall.app.coach.generated.resources.btn_join
import squadcall.app.coach.generated.resources.btn_joining
import squadcall.app.coach.generated.resources.join_code_label
import squadcall.app.coach.generated.resources.join_code_placeholder
import squadcall.app.coach.generated.resources.join_intro
import squadcall.app.coach.generated.resources.join_title
import squadcall.app.coach.generated.resources.msg_join_expired
import squadcall.app.coach.generated.resources.msg_join_failed
import squadcall.app.coach.generated.resources.msg_join_no_room
import squadcall.app.coach.generated.resources.msg_join_not_found
import squadcall.app.coach.generated.resources.msg_join_used

/** Joining someone's age group as staff with the code they shared (REQUIREMENTS §5c). */
@Composable
fun JoinWithCodeScreen(
    onNavigateBack: () -> Unit,
    onJoined: () -> Unit,
    viewModel: JoinWithCodeViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.joined.collect { onJoined() } }

    JoinWithCodeContent(
        uiState = uiState,
        onCodeChanged = viewModel::onCodeChanged,
        onJoin = viewModel::onJoinClicked,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun JoinWithCodeContent(
    uiState: JoinWithCodeUiState,
    onCodeChanged: (String) -> Unit,
    onJoin: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val error = uiState.refusal?.let {
        stringResource(
            when (it) {
                JoinCode.Refusal.NOT_FOUND -> Res.string.msg_join_not_found
                JoinCode.Refusal.EXPIRED -> Res.string.msg_join_expired
                JoinCode.Refusal.USED_OR_ALREADY_ON -> Res.string.msg_join_used
                JoinCode.Refusal.NO_ROOM_ON_PLAN -> Res.string.msg_join_no_room
                JoinCode.Refusal.OTHER -> Res.string.msg_join_failed
            }
        )
    }
    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        topBar = { SquadcallTopBar(title = stringResource(Res.string.join_title), onNavigateBack = onNavigateBack) },
        bottomBar = {
            PinnedBottomBar {
                FormErrorLine(error)
                PrimaryLoadingButton(
                    text = Res.string.btn_join,
                    submittingText = Res.string.btn_joining,
                    isLoading = uiState.isSubmitting,
                    enabled = uiState.canJoin,
                    onClick = onJoin
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormIntro(stringResource(Res.string.join_intro))
            FormTextField(
                value = uiState.code,
                onValueChange = onCodeChanged,
                label = stringResource(Res.string.join_code_label),
                placeholder = stringResource(Res.string.join_code_placeholder),
                enabled = !uiState.isSubmitting,
                isError = error != null,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
                onImeAction = onJoin
            )
        }
    }
}

@Preview
@Composable
private fun JoinWithCodePreview() {
    SquadcallTheme {
        JoinWithCodeContent(JoinWithCodeUiState(code = "K7Q-4MZ"), onCodeChanged = {}, onJoin = {}, onNavigateBack = {})
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun JoinWithCodeLandscapePreview() {
    SquadcallTheme {
        JoinWithCodeContent(JoinWithCodeUiState(code = "K7Q-4MZ"), onCodeChanged = {}, onJoin = {}, onNavigateBack = {})
    }
}

@Preview
@Composable
private fun JoinWithCodeRefusedPreview() {
    SquadcallTheme {
        JoinWithCodeContent(JoinWithCodeUiState(code = "K7Q-4MZ", refusal = JoinCode.Refusal.EXPIRED), onCodeChanged = {}, onJoin = {}, onNavigateBack = {})
    }
}
