package com.geeksville.mesh.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.ui.preview.NodeEntityPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun signalInfo(
    modifier: Modifier = Modifier,
    node: NodeEntity,
    isThisNode: Boolean
): Boolean {
    val text = if (isThisNode) {
        stringResource(R.string.channel_air_util).format(
            node.deviceMetrics.channelUtilization,
            node.deviceMetrics.airUtilTx
        )
    } else {
        buildList {
            if (node.channel > 0) add("ch:${node.channel}")
            if (node.hopsAway == 0) {
                if (node.snr < 100F && node.rssi < 0) {
                    add("RSSI: %d SNR: %.1f".format(node.rssi, node.snr))
                }
            } else {
                add("%s: %d".format(stringResource(R.string.hops_away), node.hopsAway))
            }
        }.joinToString(" ")
    }
    return if (text.isNotEmpty()) {
        Text(
            modifier = modifier,
            text = text,
            color = MaterialTheme.colors.onSurface,
            fontSize = MaterialTheme.typography.button.fontSize
        )
        true
    } else {
        false
    }
}

@Composable
@Preview(showBackground = true)
fun SignalInfoSimplePreview() {
    AppTheme {
        signalInfo(
            node = NodeEntity(
                num = 1,
                lastHeard = 0,
                channel = 0,
                snr = 12.5F,
                rssi = -42,
                hopsAway = 0
            ),
            isThisNode = false
        )
    }
}

@PreviewLightDark
@Composable
fun SignalInfoPreview(
    @PreviewParameter(NodeEntityPreviewParameterProvider::class)
    node: NodeEntity
) {
    AppTheme {
        signalInfo(
            node = node,
            isThisNode = false
        )
    }
}

@Composable
@PreviewLightDark
fun SignalInfoSelfPreview(
    @PreviewParameter(NodeEntityPreviewParameterProvider::class)
    node: NodeEntity
) {
    AppTheme {
        signalInfo(
            node = node,
            isThisNode = true
        )
    }
}
