package com.openvehicles.OVMS.ui2.components.quickactions

import android.content.Context
import com.openvehicles.OVMS.R
import com.openvehicles.OVMS.api.ApiService

/**
 * Quick action toggling "pre-condition without the charging cable" (VW e-Golf).
 *
 * The setting is not held in the app or in OVMS — it lives in the car's stored
 * BatteryControl profile, as bit 0x04 of the profile's operation byte. The
 * module reads that profile, flips the single bit and writes the record back;
 * `xvg onbat on|off` is its command for that. With the bit clear the car
 * refuses to pre-condition unless it is plugged in.
 *
 * The car does not answer a read request for its profile — it only publishes it
 * on change — so the module keeps the last one it saw. `xvg ccstatus` reports
 * the current state, which [ClimateFragment] queries and pushes in here via
 * [setKnownState].
 */
class ClimateOnBatteryQuickAction(
    apiServiceGetter: () -> ApiService?,
    context: Context? = null
) : QuickAction(
    ACTION_ID, R.drawable.ic_battery, apiServiceGetter,
    actionOnTint = R.attr.colorSecondaryContainer,
    actionOffTint = R.color.cardview_dark_background,
    label = context?.getString(R.string.climate_on_battery_short)
) {
    companion object {
        const val ACTION_ID = "climate_on_battery"

        /** Last state reported by the module, null until `xvg ccstatus` answers. */
        @JvmStatic
        var knownState: Boolean? = null
    }

    override fun onAction() {
        // Unknown state: ask for it rather than guessing a direction.
        val current = knownState ?: run {
            sendCommand("7,xvg ccstatus")
            return
        }
        sendCommand(if (current) "7,xvg onbat off" else "7,xvg onbat on")
        knownState = !current
        setActionState(knownState == true)
    }

    /**
     * The button must show what the car holds, not what we assumed when the
     * tap went out. A write is answered by the module's own confirmation, but
     * the setting can equally change from the car's own menus or another
     * client — so after every successful command we ask again and take the
     * answer as truth.
     */
    override fun onCommandSuccess(command: String, details: String?) {
        val status = Regex("onbat=([01])").find(details ?: "")
        if (status != null) {
            // This was the status query — adopt it silently, no toast.
            knownState = status.groupValues[1] == "1"
            setActionState(knownState == true)
            return
        }
        super.onCommandSuccess(command, details)
        sendCommand("7,xvg ccstatus")
    }

    override fun getStateFromCarData(): Boolean {
        return knownState == true
    }

    /**
     * The icon says where the energy comes from: a battery when the car may
     * pre-condition off the traction battery, a plug when it insists on being
     * connected. Clearer than one icon that only changes colour.
     */
    override fun getLiveCarIconId(state: Boolean): Int {
        return if (state) R.drawable.ic_battery else R.drawable.ic_charging
    }

    override fun commandsAvailable(): Boolean {
        return getCarData()?.car_type == "VWEG"
    }
}
