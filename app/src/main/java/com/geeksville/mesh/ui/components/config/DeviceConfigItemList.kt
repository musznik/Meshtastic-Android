package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ConfigProtos.Config.DeviceConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun DeviceConfigItemList(
    deviceConfig: DeviceConfig,
    enabled: Boolean,
    onSaveClicked: (DeviceConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var deviceInput by rememberSaveable { mutableStateOf(deviceConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Device Config") }

        item {
            DropDownPreference(title = "Role",
                enabled = enabled,
                items = DeviceConfig.Role.entries
                    .filter { it != DeviceConfig.Role.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = deviceInput.role,
                onItemSelected = { deviceInput = deviceInput.copy { role = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Redefine PIN_BUTTON",
                value = deviceInput.buttonGpio,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { buttonGpio = it }
                })
        }

        item {
            EditTextPreference(title = "Redefine PIN_BUZZER",
                value = deviceInput.buzzerGpio,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { buzzerGpio = it }
                })
        }

        item {
            DropDownPreference(title = "Rebroadcast mode",
                enabled = enabled,
                items = DeviceConfig.RebroadcastMode.entries
                    .filter { it != DeviceConfig.RebroadcastMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = deviceInput.rebroadcastMode,
                onItemSelected = { deviceInput = deviceInput.copy { rebroadcastMode = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "NodeInfo broadcast interval (seconds)",
                value = deviceInput.nodeInfoBroadcastSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { nodeInfoBroadcastSecs = it }
                })
        }

        item {
            SwitchPreference(title = "Double tap as button press",
                checked = deviceInput.doubleTapAsButtonPress,
                enabled = enabled,
                onCheckedChange = {
                    deviceInput = deviceInput.copy { doubleTapAsButtonPress = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Disable triple-click",
                checked = deviceInput.disableTripleClick,
                enabled = enabled,
                onCheckedChange = {
                    deviceInput = deviceInput.copy { disableTripleClick = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "POSIX Timezone",
                value = deviceInput.tzdef,
                maxSize = 64, // tzdef max_size:65
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { tzdef = it }
                },
            )
        }

        item {
            SwitchPreference(title = "Disable LED heartbeat",
                checked = deviceInput.ledHeartbeatDisabled,
                enabled = enabled,
                onCheckedChange = {
                    deviceInput = deviceInput.copy { ledHeartbeatDisabled = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = deviceInput != deviceConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    deviceInput = deviceConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(deviceInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceConfigPreview() {
    DeviceConfigItemList(
        deviceConfig = DeviceConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
