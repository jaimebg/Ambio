package com.jbgsoft.ambio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat

/**
 * Explains what notifications are for, then asks for POST_NOTIFICATIONS.
 *
 * Ambio's whole background story runs through AudioService's notification: on Android 13+
 * a user who never granted this gets playback with no transport controls in the shade and
 * none on the lock screen, and no way to stop the mix short of finding the app again.
 *
 * The system prompt is never the first thing shown. It offers no room to say why, and
 * arriving unannounced on a first launch it reads as an app grabbing at something, which
 * is how a permission that this app genuinely needs gets denied out of reflex. So the
 * rationale comes first and the system prompt only follows an explicit "Continue" — the
 * order Android's own permission guidance asks for.
 *
 * The moment is first composition rather than first play. The contextual moment would be
 * better still, but the only signal for it lives behind HomeEvent.PlayPause inside
 * feature:home, and reaching it would mean teaching a feature module about runtime
 * permissions to move the prompt by one tap. Home *is* the play screen and is already
 * drawn behind the dialog, so the explanation arrives with the thing it describes on
 * screen. If a mix-started signal ever surfaces in the app module, move this to it.
 *
 * Either answer is final for the launch, and a decline is never re-prompted: playback
 * still works without the permission, and Android silently auto-denies after the second
 * refusal anyway, so nagging spends the user's goodwill for nothing.
 */
@Composable
fun NotificationPermissionGate() {
    // Below 33 the permission is granted at install time; there is nothing to ask for, and
    // showing the rationale would explain a dialog that is never coming.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current

    // Saveable, not remembered: a rotation while either dialog is up recreates the
    // composition, and plain remember would restart the flow behind the visible dialog.
    var settled by rememberSaveable { mutableStateOf(false) }
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Nothing to undo either way; the mix plays regardless. */ }

    LaunchedEffect(Unit) {
        if (settled) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) settled = true else showRationale = true
    }

    if (!showRationale) return

    AlertDialog(
        onDismissRequest = {
            // Tapping outside is a "not now", not a deferral: re-asking on the next
            // recomposition would trap the user in a dialog they just dismissed.
            showRationale = false
            settled = true
        },
        icon = {
            Icon(imageVector = Icons.Outlined.NotificationsActive, contentDescription = null)
        },
        title = { Text(stringResource(R.string.notification_rationale_title)) },
        text = { Text(stringResource(R.string.notification_rationale_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    showRationale = false
                    settled = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            ) {
                Text(stringResource(R.string.notification_rationale_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showRationale = false
                    settled = true
                }
            ) {
                Text(stringResource(R.string.notification_rationale_dismiss))
            }
        }
    )
}
