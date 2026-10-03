package org.openmobifitness.core

/** Reported capabilities/status, separate from live exercise measurements. */
data class MobiAxis(val mode: Int, val minimum: Double, val maximum: Double) {
    val readable get() = mode in 2..3
    val writable get() = mode==1 || mode==3
}
data class MobiInteraction(val command: Int, val value: Double)
data class MobiDeviceDetails(
    val speed: MobiAxis? = null,
    val incline: MobiAxis? = null,
    val supportFold: Boolean = false,
    val foldedState: Int? = null,
    val skippingControl: Boolean = false,
    val dumbbellControl: Boolean = false,
    val tmallControl: Boolean = false,
    val interaction: MobiInteraction? = null,
    val gamePad: Int? = null,
    val voiceCommand: Int? = null,
    val errorCode: Int? = null,
    val emergencyStop: Boolean = false,
    val battery: Int? = null
)
