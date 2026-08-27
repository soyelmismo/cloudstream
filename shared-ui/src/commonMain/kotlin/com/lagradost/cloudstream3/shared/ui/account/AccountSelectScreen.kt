package com.lagradost.cloudstream3.shared.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ConfirmDeleteDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.DangerButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.OutlinedActionButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SubtitleText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.TitleText
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountEvent
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountState
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountViewModel

/**
 * Screen for selecting, creating, editing, and managing user profiles.
 */
@Composable
fun AccountSelectScreen(
    viewModel: AccountViewModel,
    onProfileSelected: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                TitleText(
                    textRes = if (state.isManageMode) Res.string.manageProfiles else Res.string.whoIsWatching,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                BodyMutedText(
                    textRes = if (state.isManageMode) Res.string.select_profile_to_manage else Res.string.select_profile_to_watch,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Profiles Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(state.accounts, key = { it.keyIndex }) { account ->
                    ProfileCard(
                        account = account,
                        isActive = account == state.activeAccount,
                        isManageMode = state.isManageMode,
                        onClick = {
                            if (state.isManageMode) {
                                viewModel.onEvent(AccountEvent.OpenEditDialog(account))
                            } else {
                                if (!account.lockPin.isNullOrBlank()) {
                                    viewModel.onEvent(AccountEvent.SelectAccount(account))
                                } else {
                                    onProfileSelected(account)
                                }
                            }
                        }
                    )
                }

                // Add Profile button if max accounts limit not reached (e.g. 6)
                if (state.accounts.size < 6) {
                    item {
                        AddProfileCard(
                            onClick = {
                                viewModel.onEvent(AccountEvent.OpenCreateDialog)
                            }
                        )
                    }
                }
            }

            // Footer / Bottom Actions
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                OutlinedActionButton(
                    textRes = if (state.isManageMode) Res.string.doneManagingProfiles else Res.string.manageProfiles,
                    onClick = { viewModel.onEvent(AccountEvent.ToggleManageMode) },
                    icon = if (state.isManageMode) Icons.Default.Check else Icons.Default.Edit,
                    borderColor = if (state.isManageMode) CloudStreamColors.Primary else CloudStreamColors.Divider,
                    contentColor = if (state.isManageMode) CloudStreamColors.Primary else CloudStreamColors.TextSecondary,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }

        // Dialogs for Create / Edit / PIN Entry
        if (state.isCreateDialogOpen) {
            ProfileFormDialog(
                titleRes = Res.string.addProfile,
                confirmButtonTextRes = Res.string.createProfile,
                initialName = "",
                initialAvatarIndex = state.accounts.size % AccountViewModel.AVATAR_COLORS.size,
                initialPin = "",
                onDismiss = { viewModel.onEvent(AccountEvent.CloseCreateDialog) },
                onConfirm = { name, avatarIndex, pin ->
                    viewModel.onEvent(AccountEvent.CreateAccount(name, avatarIndex, pin))
                }
            )
        }

        state.editingAccount?.let { account ->
            ProfileFormDialog(
                titleRes = Res.string.editProfile,
                confirmButtonTextRes = Res.string.apply,
                initialName = account.name,
                initialAvatarIndex = account.defaultImageIndex,
                initialPin = account.lockPin ?: "",
                isEditing = true,
                onDismiss = { viewModel.onEvent(AccountEvent.CloseEditDialog) },
                onConfirm = { name, avatarIndex, pin ->
                    viewModel.onEvent(AccountEvent.UpdateAccount(account.keyIndex, name, avatarIndex, pin))
                },
                onDelete = if (state.accounts.size > 1) {
                    {
                        viewModel.onEvent(AccountEvent.CloseEditDialog)
                        deletingAccount = account
                    }
                } else null
            )
        }

        deletingAccount?.let { account ->
            ConfirmDeleteDialog(
                onConfirm = {
                    viewModel.onEvent(AccountEvent.DeleteAccount(account.keyIndex))
                    deletingAccount = null
                },
                onDismiss = { deletingAccount = null },
                titleRes = Res.string.deleteProfileConfirmTitle,
                messageRes = Res.string.deleteProfileConfirmDesc,
                confirmTextRes = Res.string.deleteProfile
            )
        }

        state.pinPromptAccount?.let { account ->
            PinEntryDialog(
                account = account,
                hasError = state.pinError,
                onDismiss = { viewModel.onEvent(AccountEvent.DismissPinPrompt) },
                onSubmitPin = { enteredPin ->
                    viewModel.onEvent(AccountEvent.SelectAccount(account, enteredPin))
                    if (account.lockPin == enteredPin) {
                        onProfileSelected(account)
                    }
                }
            )
        }
    }
}

/**
 * Interactive Profile Card with Avatar, Name, and Status Badges.
 */
@Composable
private fun ProfileCard(
    account: AccountEntity,
    isActive: Boolean,
    isManageMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isHovered || isFocused

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.10f else 1.0f,
        animationSpec = tween(150)
    )

    val avatarColor = Color(
        AccountViewModel.AVATAR_COLORS.getOrElse(account.defaultImageIndex) { AccountViewModel.DEFAULT_AVATAR_COLOR }
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(96.dp)
        ) {
            // Main Avatar Circle
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            width = if (isHighlighted) 3.dp else if (isActive) 2.dp else 0.dp,
                            color = if (isHighlighted) CloudStreamColors.Primary else if (isActive) CloudStreamColors.PrimaryVariant else Color.Transparent
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = account.name.take(1).uppercase(),
                    style = MaterialTheme.typography.h4.copy(
                        color = MaterialTheme.colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                )
            }

            // Edit Overlay Icon when in Manage Mode
            if (isManageMode) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(CloudStreamColors.Background.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.edit),
                        tint = MaterialTheme.colors.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Lock PIN indicator
            if (account.lockPin != null && account.lockPin.isNotBlank() && !isManageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .background(CloudStreamColors.SurfaceElevated, CircleShape)
                        .clip(CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(Res.string.pin),
                        tint = CloudStreamColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Profile Name
        Text(
            text = account.name,
            style = MaterialTheme.typography.body1.copy(
                fontWeight = if (isHighlighted || isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isHighlighted) CloudStreamColors.Primary else if (isActive) CloudStreamColors.Primary else CloudStreamColors.TextPrimary,
                fontSize = 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Card for creating a new profile.
 */
@Composable
private fun AddProfileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isHovered || isFocused

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.10f else 1.0f,
        animationSpec = tween(150)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    if (isHighlighted) CloudStreamColors.SurfaceElevated else CloudStreamColors.SurfaceVariant
                )
                .border(
                    BorderStroke(
                        width = if (isHighlighted) 2.dp else 1.dp,
                        color = if (isHighlighted) CloudStreamColors.Primary else CloudStreamColors.Divider
                    ),
                    shape = CircleShape
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.addProfile),
                tint = if (isHighlighted) CloudStreamColors.Primary else CloudStreamColors.TextSecondary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        BodyMutedText(
            textRes = Res.string.addProfile,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Modal dialog to Create or Edit a user profile using Design System ActionDialog and CloudStreamTextField.
 */
@Composable
private fun ProfileFormDialog(
    titleRes: org.jetbrains.compose.resources.StringResource,
    confirmButtonTextRes: org.jetbrains.compose.resources.StringResource,
    initialName: String,
    initialAvatarIndex: Int,
    initialPin: String,
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, avatarIndex: Int, pin: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedAvatar by remember { mutableStateOf(initialAvatarIndex) }
    var pin by remember { mutableStateOf(initialPin) }

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = titleRes,
        confirmTextRes = confirmButtonTextRes,
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            if (name.isNotBlank()) {
                onConfirm(name.trim(), selectedAvatar, pin.ifBlank { null })
            }
        },
        cancelTextRes = Res.string.cancel,
        onCancel = onDismiss,
        neutralTextRes = if (isEditing && onDelete != null) Res.string.deleteProfile else null,
        onNeutral = if (isEditing && onDelete != null) onDelete else null,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name Field
                CloudStreamTextField(
                    value = name,
                    onValueChange = { name = it },
                    labelRes = Res.string.profileName,
                    placeholderRes = Res.string.profileNamePlaceholder,
                    singleLine = true
                )

                // Avatar Color Palette with focus support
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleText(
                        textRes = Res.string.chooseAvatar,
                        fontWeight = FontWeight.SemiBold,
                        color = CloudStreamColors.TextSecondary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AccountViewModel.AVATAR_COLORS.forEachIndexed { index, colorValue ->
                            val isSelected = selectedAvatar == index
                            val interactionSource = remember { MutableInteractionSource() }
                            val isHovered by interactionSource.collectIsHoveredAsState()
                            val isFocused by interactionSource.collectIsFocusedAsState()
                            val isHighlighted = isHovered || isFocused

                            val scale by animateFloatAsState(
                                targetValue = if (isHighlighted) 1.25f else 1.0f,
                                animationSpec = tween(150)
                            )

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .scale(scale)
                                    .clip(CircleShape)
                                    .background(Color(colorValue))
                                    .border(
                                        BorderStroke(
                                            width = if (isHighlighted) 2.dp else 0.dp,
                                            color = if (isHighlighted) CloudStreamColors.Primary else Color.Transparent
                                        ),
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { selectedAvatar = index }
                                    )
                                    .focusable(interactionSource = interactionSource),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colors.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Optional PIN Field
                CloudStreamTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pin = it },
                    labelRes = Res.string.profilePinOptional,
                    placeholderRes = Res.string.pin_four_digits_placeholder,
                    isPassword = true,
                    singleLine = true
                )
            }
        }
    )
}

/**
 * Dialog for entering 4-digit PIN lock to switch profile using ActionDialog and CloudStreamTextField.
 */
@Composable
private fun PinEntryDialog(
    account: AccountEntity,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    val titleString = "${stringResource(Res.string.enterPin)} (${account.name})"

    ActionDialog(
        onDismissRequest = onDismiss,
        title = titleString,
        confirmTextRes = Res.string.confirm,
        confirmEnabled = enteredPin.length == 4,
        onConfirm = { onSubmitPin(enteredPin) },
        cancelTextRes = Res.string.cancel,
        onCancel = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CloudStreamTextField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            enteredPin = it
                            if (it.length == 4) {
                                onSubmitPin(it)
                            }
                        }
                    },
                    labelRes = Res.string.pin_four_digits_label,
                    isPassword = true,
                    isError = hasError,
                    errorRes = if (hasError) Res.string.invalidPin else null,
                    singleLine = true
                )
            }
        }
    )
}


