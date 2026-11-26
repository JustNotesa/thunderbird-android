package com.fsck.k9.ui.settings.offline

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.BaseActivity
import com.fsck.k9.ui.base.extensions.fragmentTransaction

/**
 * Hosts the settings for the messages that are available offline.
 */
class OfflineSettingsActivity : BaseActivity() {
    override val supportsOneHandedMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLayout(R.layout.general_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.offline_settings_title)

        if (savedInstanceState == null) {
            fragmentTransaction {
                replace(
                    R.id.generalSettingsContainer,
                    OfflineSettingsFragment(),
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OfflineSettingsActivity::class.java))
        }
    }
}
