@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "DestructuringDeclarationWithTooManyEntries",
    "MagicNumber",
    "CyclomaticComplexMethod",
)

package com.geeksville.mesh.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ConfigProtos.Config.DeviceConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.compose.ElevationInfo
import com.geeksville.mesh.ui.compose.SatelliteCountInfo
import com.geeksville.mesh.ui.preview.NodeInfoPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.metersIn

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NodeItem(
    thisNodeInfo: NodeInfo?,
    thatNodeInfo: NodeInfo,
    gpsFormat: Int,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    isIgnored: Boolean = false,
    chipClicked: () -> Unit = {},
    blinking: Boolean = false,
    expanded: Boolean = false,
    currentTimeMillis: Long,
    hasPublicKey: Boolean = false,
) {
    val isUnknownUser = thatNodeInfo.user?.hwModel == MeshProtos.HardwareModel.UNSET
    val unknownShortName = stringResource(id = R.string.unknown_node_short_name)
    val longName = thatNodeInfo.user?.longName ?: stringResource(id = R.string.unknown_username)

    val nodeName = if (hasPublicKey) "🔒 $longName" else longName
    val isThisNode = thisNodeInfo?.num == thatNodeInfo.num
    val distance = thisNodeInfo?.distanceStr(thatNodeInfo, distanceUnits)
    val (textColor, nodeColor) = thatNodeInfo.colors

    val position = thatNodeInfo.position
    val hwInfoString = thatNodeInfo.user?.hwModelString ?: MeshProtos.HardwareModel.UNSET.name
    val roleName =
        if (isUnknownUser) {
            DeviceConfig.Role.UNRECOGNIZED.name
        } else {
            thatNodeInfo.user?.role?.let { role ->
                DeviceConfig.Role.forNumber(role)?.name
            } ?: DeviceConfig.Role.UNRECOGNIZED.name
        }
    val nodeId = thatNodeInfo.user?.id ?: "???"

    val highlight = Color(0x33FFFFFF)
    val bgColor by animateColorAsState(
        targetValue = if (blinking) highlight else Color.Transparent,
        animationSpec = repeatable(
            iterations = 6,
            animation = tween(
                durationMillis = 250,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinking node"
    )

    val style = if (isUnknownUser) {
        LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)
    } else {
        LocalTextStyle.current
    }

    val (detailsShown, showDetails) = remember { mutableStateOf(expanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 80.dp),
        onClick = { showDetails(!detailsShown) },
    ) {
        Surface {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(bgColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Chip(
                            modifier = Modifier
                                .width(IntrinsicSize.Min)
                                .padding(end = 8.dp)
                                .defaultMinSize(minHeight = 32.dp, minWidth = 72.dp),
                            colors = ChipDefaults.chipColors(
                                backgroundColor = Color(nodeColor),
                                contentColor = Color(textColor)
                            ),
                            onClick = { chipClicked() },
                            content = {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = thatNodeInfo.user?.shortName ?: unknownShortName,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = MaterialTheme.typography.button.fontSize,
                                    textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                                    textAlign = TextAlign.Center,
                                )
                            },
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = nodeName,
                            style = style,
                            textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                            softWrap = true,
                        )

                        LastHeardInfo(
                            lastHeard = thatNodeInfo.lastHeard,
                            currentTimeMillis = currentTimeMillis
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        PacketCounterInfo(
                            packets = thatNodeInfo.sendPackets
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (distance != null) {
                            Text(
                                text = distance,
                                fontSize = MaterialTheme.typography.button.fontSize,
                            )
                        } else {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        BatteryInfo(
                            batteryLevel = thatNodeInfo.batteryLevel,
                            voltage = thatNodeInfo.voltage
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        signalInfo(
                            nodeInfo = thatNodeInfo,
                            isThisNode = isThisNode
                        )
                        if (position?.isValid() == true) {
                            val satCount = position.satellitesInView
                            if (satCount > 0) {
                                SatelliteCountInfo(
                                    satCount = satCount
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        thatNodeInfo.environmentMetrics?.getDisplayString(tempInFahrenheit)?.let { envMetrics ->
                            Text(
                                text = envMetrics,
                                color = MaterialTheme.colors.onSurface,
                                fontSize = MaterialTheme.typography.button.fontSize
                            )
                        }
                    }

                    if (detailsShown || expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DisableSelection {
                                LinkedCoordinates(
                                    position = position,
                                    format = gpsFormat,
                                    nodeName = nodeName
                                )
                            }
                            val system =
                                ConfigProtos.Config.DisplayConfig.DisplayUnits.forNumber(distanceUnits)
                            if (position?.isValid() == true) {
                                val altitude = position.altitude.metersIn(system)
                                val elevationSuffix = stringResource(id = R.string.elevation_suffix)
                                ElevationInfo(
                                    altitude = altitude,
                                    system = system,
                                    suffix = elevationSuffix
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = hwInfoString,
                                fontSize = MaterialTheme.typography.button.fontSize,
                                style = style,
                            )
                            Text(
                                modifier = Modifier.weight(1f),
                                text = roleName,
                                textAlign = TextAlign.Center,
                                fontSize = MaterialTheme.typography.button.fontSize
                            )
                            Text(
                                modifier = Modifier.weight(1f),
                                text = nodeId,
                                textAlign = TextAlign.End,
                                fontSize = MaterialTheme.typography.button.fontSize
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = false)
fun NodeInfoSimplePreview() {
    AppTheme {
        val thisNodeInfo = NodeInfoPreviewParameterProvider().values.first()
        val thatNodeInfo = NodeInfoPreviewParameterProvider().values.last()
        NodeItem(
            thisNodeInfo = thisNodeInfo,
            thatNodeInfo = thatNodeInfo,
            1,
            0,
            true,
            currentTimeMillis = System.currentTimeMillis()
        )
    }
}

@Composable
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
fun NodeInfoPreview(
    @PreviewParameter(NodeInfoPreviewParameterProvider::class)
    thatNodeInfo: NodeInfo
) {
    AppTheme {
        val thisNodeInfo = NodeInfoPreviewParameterProvider().values.first()
        Column {
            Text(
                text = "Details Collapsed",
                color = MaterialTheme.colors.onBackground
            )
            NodeItem(
                thisNodeInfo = thisNodeInfo,
                thatNodeInfo = thatNodeInfo,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = false,
                currentTimeMillis = System.currentTimeMillis()
            )
            Text(
                text = "Details Shown",
                color = MaterialTheme.colors.onBackground
            )
            NodeItem(
                thisNodeInfo = thisNodeInfo,
                thatNodeInfo = thatNodeInfo,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = true,
                currentTimeMillis = System.currentTimeMillis()
            )
        }
    }
}
