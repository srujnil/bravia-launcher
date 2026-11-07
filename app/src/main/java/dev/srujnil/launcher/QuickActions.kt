package dev.srujnil.launcher

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector

/** A shortcut in the top row. No intent means it opens something inside this app. */
data class QuickAction(val label: String, val icon: ImageVector, val intent: Intent?)

enum class NetworkKind { Ethernet, Wifi, None }

data class DeviceStatus(val network: NetworkKind, val bluetoothOn: Boolean)

fun readDeviceStatus(context: Context): DeviceStatus {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    val network = when {
        capabilities == null -> NetworkKind.None
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Ethernet
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
        else -> NetworkKind.None
    }
    return DeviceStatus(network, BluetoothAdapter.getDefaultAdapter()?.isEnabled == true)
}

fun quickActions(status: DeviceStatus): List<QuickAction> {
    val networkAction = Intent(Settings.ACTION_WIFI_SETTINGS)
    return listOf(
        QuickAction("All apps", Icons.Outlined.Apps, null),
        QuickAction(
            "Inputs",
            Icons.AutoMirrored.Outlined.Input,
            Intent("com.android.tv.action.VIEW_INPUTS").setPackage("com.android.systemui"),
        ),
        QuickAction("Picture", Icons.Outlined.Brightness6, Intent(Settings.ACTION_DISPLAY_SETTINGS)),
        // The standard sound action crashes Sony's settings app on this TV.
        QuickAction(
            "Sound",
            Icons.AutoMirrored.Outlined.VolumeUp,
            Intent().setComponent(
                ComponentName("com.sony.dtv.configsettings", "com.sony.dtv.configsettings.SoundSettingActivity"),
            ),
        ),
        when (status.network) {
            NetworkKind.Ethernet -> QuickAction("Network: Ethernet", Icons.Outlined.Lan, networkAction)
            NetworkKind.Wifi -> QuickAction("Network: Wi-Fi", Icons.Outlined.Wifi, networkAction)
            NetworkKind.None -> QuickAction("Network: not connected", Icons.Outlined.WifiOff, networkAction)
        },
        QuickAction(
            if (status.bluetoothOn) "Bluetooth: on" else "Bluetooth: off",
            if (status.bluetoothOn) Icons.Outlined.Bluetooth else Icons.Outlined.BluetoothDisabled,
            // The standard Bluetooth action doesn't exist on this TV, so this opens accessory pairing.
            Intent("com.google.android.intent.action.CONNECT_INPUT").setPackage("com.android.tv.settings"),
        ),
        QuickAction("Settings", Icons.Outlined.Settings, Intent(Settings.ACTION_SETTINGS)),
    )
}
