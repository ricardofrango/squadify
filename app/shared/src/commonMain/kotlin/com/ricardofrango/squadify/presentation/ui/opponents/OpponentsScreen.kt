package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.OpponentList
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_create_opponent
import squadify.app.shared.generated.resources.btn_create_typed_opponent
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.cd_clear_search
import squadify.app.shared.generated.resources.ic_add
import squadify.app.shared.generated.resources.ic_check
import squadify.app.shared.generated.resources.ic_chevron_right
import squadify.app.shared.generated.resources.ic_close
import squadify.app.shared.generated.resources.ic_search
import squadify.app.shared.generated.resources.label_search_opponents
import squadify.app.shared.generated.resources.msg_ask_coordinator_opponent
import squadify.app.shared.generated.resources.msg_check_connection
import squadify.app.shared.generated.resources.msg_no_opponents_body
import squadify.app.shared.generated.resources.msg_no_opponents_match
import squadify.app.shared.generated.resources.msg_no_opponents_match_hint
import squadify.app.shared.generated.resources.msg_no_opponents_title
import squadify.app.shared.generated.resources.msg_opponents_load_failed
import squadify.app.shared.generated.resources.title_choose_opponent
import squadify.app.shared.generated.resources.title_opponents

/**
 * The club's opponents (design 38a–c), one screen in two modes:
 * - SELECT ([selectMode]), from Schedule match's Opponent field: "Choose opponent", a check on
 *   [selectedOpponentId]; tapping a row picks it.
 * - BROWSE, from More → Opponents: tapping a row opens it.
 * Search filters as you type, on name and short name, ignoring case and accents. The create actions
 * (the + in the bar, the empty states' buttons) show only when the server says
 * the user may create, and when [onCreateOpponent] is given; it gets the search text to prefill Name.
 */
@Composable
fun OpponentsScreen(
    clubId: String,
    ageGroupId: String?,
    selectMode: Boolean,
    selectedOpponentId: String?,
    onNavigateBack: () -> Unit,
    onOpponentClick: (Opponent) -> Unit,
    onCreateOpponent: ((name: String) -> Unit)?,
    // True right after returning from a screen that changed opponents: reload, keeping the search.
    refresh: Boolean = false,
    viewModel: OpponentsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        val query by viewModel.query.collectAsState()
        LaunchedEffect(clubId, ageGroupId) { viewModel.load(clubId, ageGroupId) }
        LaunchedEffect(refresh) { if (refresh) viewModel.load(clubId, ageGroupId, force = true) }

        OpponentsContent(
            state = state,
            query = query,
            selectMode = selectMode,
            selectedOpponentId = selectedOpponentId,
            onQueryChange = viewModel::onQueryChange,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(clubId, ageGroupId, force = true) },
            onOpponentClick = onOpponentClick,
            onCreateOpponent = onCreateOpponent
        )
    }
}

@Composable
private fun OpponentsContent(
    state: OpponentsUiState,
    query: String,
    selectMode: Boolean,
    selectedOpponentId: String?,
    onQueryChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onOpponentClick: (Opponent) -> Unit,
    onCreateOpponent: ((name: String) -> Unit)?
) {
    val list = (state as? OpponentsUiState.Idle)?.list
    val create = onCreateOpponent?.takeIf { list?.canCreate == true }
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(if (selectMode) Res.string.title_choose_opponent else Res.string.title_opponents),
                subtitle = list?.clubName,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (create != null) {
                        IconButton(onClick = { create("") }) {
                            Icon(painterResource(Res.drawable.ic_add), stringResource(Res.string.btn_create_opponent), tint = Color.White)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().background(Color.White),
            contentAlignment = Alignment.TopCenter
        ) {
            // Rows stay readable on a landscape phone or a tablet instead of stretching edge to edge.
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                SearchField(query, onQueryChange)
                when (state) {
                    OpponentsUiState.Loading -> SkeletonRows()
                    OpponentsUiState.Error -> CenteredMessage(
                        title = stringResource(Res.string.msg_opponents_load_failed),
                        body = stringResource(Res.string.msg_check_connection)
                    ) {
                        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)) {
                            Text(stringResource(Res.string.btn_retry))
                        }
                    }
                    is OpponentsUiState.Idle -> OpponentList(
                        list = state.list,
                        query = query,
                        selectMode = selectMode,
                        selectedOpponentId = selectedOpponentId,
                        onOpponentClick = onOpponentClick,
                        onCreateOpponent = create
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(Res.string.label_search_opponents)) },
        leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null, tint = DeepCarbon.copy(alpha = 0.45f)) },
        trailingIcon = {
            // Clearing keeps the focus, so the next search can be typed straight away.
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.cd_clear_search), tint = DeepCarbon.copy(alpha = 0.55f))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
            focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
            cursorColor = PitchGreen
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun OpponentList(
    list: OpponentList,
    query: String,
    selectMode: Boolean,
    selectedOpponentId: String?,
    onOpponentClick: (Opponent) -> Unit,
    onCreateOpponent: ((name: String) -> Unit)?
) {
    val matches = filterOpponents(list.opponents, query)
    when {
        list.opponents.isEmpty() -> CenteredMessage(
            title = stringResource(Res.string.msg_no_opponents_title),
            body = stringResource(Res.string.msg_no_opponents_body)
        ) {
            if (onCreateOpponent != null) CreateButton("+ ${stringResource(Res.string.btn_create_opponent)}") { onCreateOpponent(query.trim()) }
        }

        matches.isEmpty() -> CenteredMessage(
            title = stringResource(Res.string.msg_no_opponents_match, query.trim()),
            body = stringResource(if (onCreateOpponent != null) Res.string.msg_no_opponents_match_hint else Res.string.msg_ask_coordinator_opponent)
        ) {
            if (onCreateOpponent != null) {
                CreateButton("+ ${stringResource(Res.string.btn_create_typed_opponent, query.trim())}") { onCreateOpponent(query.trim()) }
            }
        }

        else -> {
            val groups = groupByLetter(matches)
            LazyColumn(Modifier.fillMaxSize()) {
                letterGroups(groups, selectMode, selectedOpponentId, onOpponentClick)
            }
        }
    }
}

private fun LazyListScope.letterGroups(
    groups: List<Pair<String, List<Opponent>>>,
    selectMode: Boolean,
    selectedOpponentId: String?,
    onOpponentClick: (Opponent) -> Unit
) {
    groups.forEach { (letter, opponents) ->
        stickyHeader(key = "letter-$letter") { SectionHeader(letter) }
        items(opponents, key = { it.id }) {
            OpponentRow(it, selectMode, selected = it.id == selectedOpponentId, onClick = { onOpponentClick(it) })
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = DeepCarbon.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF6F6F7)).padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/** What a row says under the name: the short name, then the stadium when there is one. */
internal fun opponentSubline(opponent: Opponent): String? =
    listOfNotNull(opponent.shortName, opponent.stadiumName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifEmpty { null }

@Composable
private fun OpponentRow(opponent: Opponent, selectMode: Boolean, selected: Boolean, onClick: () -> Unit) {
    val highlighted = selectMode && selected
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(if (highlighted) PitchGreen.copy(alpha = 0.08f) else Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OpponentLogo(opponent.name, opponent.shortName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    opponent.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                opponentSubline(opponent)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                highlighted -> Icon(painterResource(Res.drawable.ic_check), null, tint = PitchGreen, modifier = Modifier.size(22.dp))
                !selectMode -> Icon(painterResource(Res.drawable.ic_chevron_right), null, tint = DeepCarbon.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
            }
        }
        HorizontalDivider(color = CarbonGray.copy(alpha = 0.08f))
    }
}

@Composable
private fun CreateButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CenteredMessage(title: String, body: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        action()
    }
}

@Composable
private fun SkeletonRows() {
    Column {
        repeat(7) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(CarbonGray.copy(alpha = 0.06f), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(160.dp).height(12.dp).background(CarbonGray.copy(alpha = 0.06f), RoundedCornerShape(6.dp)))
                    Box(Modifier.width(100.dp).height(10.dp).background(CarbonGray.copy(alpha = 0.05f), RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

// ---- Previews ----

private val previewList = OpponentList(
    clubName = "CD Ribamar",
    opponents = listOf(
        Opponent("o1", "c1", "AD Fontelas", "ADF"),
        Opponent("o2", "c1", "Académica Foz do Vez", "AFV"),
        Opponent("o3", "c1", "Atlético Ericeira", "ATE"),
        Opponent("o4", "c1", "CF Os Malveirenses", "CFM"),
        Opponent("o5", "c1", "GD Sobreirense")
    ),
    canCreate = true
)

@Preview
@Composable
private fun OpponentsBrowsePreview() {
    SquadifyTheme {
        OpponentsContent(OpponentsUiState.Idle(previewList), "", selectMode = false, selectedOpponentId = null, {}, {}, {}, {}, onCreateOpponent = {})
    }
}

@Preview
@Composable
private fun OpponentsSelectPreview() {
    SquadifyTheme {
        OpponentsContent(OpponentsUiState.Idle(previewList), "", selectMode = true, selectedOpponentId = "o1", {}, {}, {}, {}, onCreateOpponent = {})
    }
}

@Preview
@Composable
private fun OpponentsNoMatchPreview() {
    SquadifyTheme {
        OpponentsContent(OpponentsUiState.Idle(previewList), "Sporting Lourel", selectMode = true, selectedOpponentId = null, {}, {}, {}, {}, onCreateOpponent = {})
    }
}

@Preview
@Composable
private fun OpponentsEmptyPreview() {
    SquadifyTheme {
        OpponentsContent(OpponentsUiState.Idle(previewList.copy(opponents = emptyList())), "", selectMode = false, selectedOpponentId = null, {}, {}, {}, {}, onCreateOpponent = {})
    }
}

@Preview
@Composable
private fun OpponentsErrorPreview() {
    SquadifyTheme {
        OpponentsContent(OpponentsUiState.Error, "", selectMode = false, selectedOpponentId = null, {}, {}, {}, {}, onCreateOpponent = null)
    }
}
