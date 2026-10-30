package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.presentation.ui.account.AccountScreen
import com.ricardofrango.squadify.presentation.ui.changeemail.ChangeEmailScreen
import com.ricardofrango.squadify.presentation.ui.forgotpassword.ForgotPasswordScreen

// The account screens both apps have (TECHNICAL_DECISIONS §72): Account, Change email and Forgot
// password. Each app registers them with `accountScreens(navigator)`; its Sign in reads
// KEY_PASSWORD_RESET_EMAIL.

// Forgot password -> Login: the email whose password was just reset (prefilled, with a confirmation).
const val KEY_PASSWORD_RESET_EMAIL = "passwordResetEmail"

// Change email -> Account: the new address, shown with a confirmation.
private const val KEY_EMAIL_CHANGED = "emailChanged"

fun ScreenRegistry.accountScreens(navigator: AppNavigator) {
    screen<Screen.ForgotPassword> { entry ->
        val loginEntry = remember(entry) { navigator.previousBackStackEntry }
        ForgotPasswordScreen(
            initialEmail = entry.toRoute<Screen.ForgotPassword>().email,
            onNavigateBack = { navigator.popBackStack() },
            onPasswordReset = { email ->
                navigator.setIfAlive(loginEntry, KEY_PASSWORD_RESET_EMAIL, email)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.ChangeEmail> { entry ->
        val accountEntry = remember(entry) { navigator.previousBackStackEntry }
        ChangeEmailScreen(
            currentEmail = entry.toRoute<Screen.ChangeEmail>().currentEmail.ifEmpty { null },
            onNavigateBack = { navigator.popBackStack() },
            onEmailChanged = { email ->
                navigator.setIfAlive(accountEntry, KEY_EMAIL_CHANGED, email)
                navigator.popBackStack()
            },
            onSignedOut = {
                navigator.navigate(Screen.Login) { popUpToRoot() }
            }
        )
    }

    screen<Screen.Account> { entry ->
        val changedEmail by entry.savedStateHandle.getStateFlow<String?>(KEY_EMAIL_CHANGED, null).collectAsStateWithLifecycle()
        AccountScreen(
            changedEmail = changedEmail,
            onNavigateToChangeEmail = { email -> navigator.navigate(Screen.ChangeEmail(email)) },
            // Only offered where AppFeatures.accountDeletion is on; that app registers the screen.
            onNavigateToDeleteAccount = { navigator.navigate(Screen.DeleteAccount) },
            onNavigateBack = {
                navigator.popBackStack()
            },
            // Clear the whole back stack: in the clubs app a single-club user has no My clubs
            // underneath, and a club left under Sign in would also draw it in that club's colours.
            onNavigateToLogin = {
                navigator.navigate(Screen.Login) {
                    popUpToRoot()
                }
            }
        )
    }
}
