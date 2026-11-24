package com.arst.diabetesassistant.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.alarm.Notifier
import com.arst.diabetesassistant.alarm.Scheduler
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.util.localized

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        Store.init(newBase.applicationContext)
        super.attachBaseContext(newBase.localized())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { MainScreen(onLanguageChanged = { applyLanguage() }) } }
    }

    override fun onResume() {
        super.onResume()
        // e.g. user just granted exact-alarm permission in system settings
        Scheduler.rescheduleAll(this)
    }

    private fun applyLanguage() {
        Notifier.createChannels(applicationContext)
        com.arst.diabetesassistant.widget.NextDoseWidget.updateAll(this)
        recreate()
    }
}

private enum class Tab(val title: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Filled.Home),
    MEDS(R.string.tab_meds, Icons.Filled.List),
    GLUCOSE(R.string.tab_glucose, Icons.Filled.Favorite),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(onLanguageChanged: () -> Unit) {
    val data by Store.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(Tab.entries[tab].title)) })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.title)) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (Tab.entries[tab]) {
            Tab.TODAY -> TodayScreen(data, m, onGoToMeds = { tab = 1 }, onGoToGlucose = { tab = 2 })
            Tab.MEDS -> MedsScreen(data, m)
            Tab.GLUCOSE -> GlucoseScreen(data, m)
            Tab.SETTINGS -> SettingsScreen(data, m, onLanguageChanged)
        }
    }
}
