package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_remove_photo
import squadify.app.shared.generated.resources.label_add_photo
import squadify.app.shared.generated.resources.label_change_photo
import squadify.app.shared.generated.resources.msg_photo_hint
import squadify.app.shared.generated.resources.msg_photo_saving

/**
 * A person's photo (a player's, or a user's own), or their initials when there's none or it can't
 * load. [photoUrl] is the server path; the server stores it as a square, so it fills the circle.
 * See TECHNICAL_DECISIONS.md §57.
 */
@Composable
fun PersonPhoto(
    photoUrl: String?,
    initials: String,
    size: Dp,
    background: Color,
    initialsColor: Color,
    modifier: Modifier = Modifier,
    initialsStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
) = ClubLogo(
    logoUrl = photoUrl,
    initials = initials,
    modifier = modifier,
    size = size,
    background = background,
    initialsColor = initialsColor,
    contentScale = ContentScale.Crop,
    initialsStyle = initialsStyle
)

/** A photo that's saved straight away (Account, Edit player): its path, while it's saving, the last error. */
data class PhotoState(
    val url: String? = null,
    val isUpdating: Boolean = false,
    val error: String? = null
)

/**
 * "Add photo" / "Change photo" and "Remove", with the hint, "Saving…" or the error under them. The
 * picker opens the platform's gallery; [onPicked] gets the image's bytes.
 */
@Composable
fun PhotoActions(photo: PhotoState, onPicked: (ByteArray) -> Unit, onRemove: () -> Unit) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch { onPicked(file.readBytes()) }
    }
    val enabled = !photo.isUpdating
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (photo.url == null) Res.string.label_add_photo else Res.string.label_change_photo),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) PitchGreen else PitchGreen.copy(alpha = 0.4f),
                modifier = Modifier.clickable(enabled = enabled) { picker.launch() }.padding(vertical = 4.dp)
            )
            if (photo.url != null) {
                Text(" · ", color = DeepCarbon.copy(alpha = 0.3f))
                Text(
                    stringResource(Res.string.btn_remove_photo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) AlertRed else AlertRed.copy(alpha = 0.4f),
                    modifier = Modifier.clickable(enabled = enabled, onClick = onRemove).padding(vertical = 4.dp)
                )
            }
        }
        Text(
            photo.error ?: stringResource(if (photo.isUpdating) Res.string.msg_photo_saving else Res.string.msg_photo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (photo.error != null) MaterialTheme.colorScheme.error else DeepCarbon.copy(alpha = 0.5f)
        )
    }
}
