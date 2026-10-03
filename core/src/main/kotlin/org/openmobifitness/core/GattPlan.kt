package org.openmobifitness.core

data class GattEndpoint(val service: String, val characteristic: String, val properties: Int)
enum class GattAction { MTU, READ, NOTIFY, REQUEST_CONTROL }
data class GattStep(val action: GattAction, val endpoint: GattEndpoint? = null, val delayMs: Long = 0)

/** The handlers walk the discovered service/characteristic order, interleaving reads
 * and subscriptions. Sorting UUIDs or subscribing to everything first changes that order.
 * Source: both reference APKs' BaseHandler and *Handler.handlerData (see research notes).
 */
object GattPlan {
    // BleGattMangerQueue delays each operation's completion by 300 ms before dequeuing the next.
    const val OPERATION_GAP_MS = 300L
    fun initialization(protocol: Protocol, name: String, endpoints: List<GattEndpoint>): List<GattStep> = buildList {
        add(GattStep(GattAction.MTU))
        if(protocol==Protocol.FTMS && MobiProfiles.officialFtms(name)) endpoints.firstOrNull { it.service=="1826" && it.characteristic=="2ad9" }?.let {
            add(GattStep(GattAction.REQUEST_CONTROL,it))
        }
        if(protocol==Protocol.HUANTONG) endpoints.firstOrNull { it.service=="fff0" && it.characteristic=="fff1" }?.let {
            add(GattStep(GattAction.NOTIFY,it))
        }
        for(e in endpoints) {
            val c=e.characteristic
            val read=when(protocol) {
                Protocol.V2 -> when(e.service) {
                    "180a" -> true
                    "180f" -> c=="2a19"
                    "8800" -> c in setOf("8801","8802","8803","8804","8805","8806","8807","8808","8812")
                    "8900" -> c=="8901"
                    else -> false
                }
                Protocol.FTMS -> when(e.service) {
                    "180a" -> true
                    "180f" -> c=="2a19"
                    "1826" -> c in setOf("2acc","2ad3","2ad4","2ad5","2ad6","2ad7","2ad8")
                    else -> false
                }
                else -> false
            }
            val notify=when(protocol) {
                Protocol.V1 -> e.service in setOf("ffe0","fff0","ffc0") && c in setOf("ffe4","ffe1","ffea","fff4","ffeb")
                Protocol.V2 -> when(e.service) {
                    "180d" -> c=="2a37"
                    "180f" -> c=="2a19"
                    "8800" -> c in setOf("880e","8811","8812","8813","8814","88e1")
                    "8900" -> c=="8902"
                    "8a00" -> c=="8a01"
                    else -> false
                }
                Protocol.HUANTONG -> e.service in setOf("ffe0","fff0","ffc0") && c in setOf("ffe4","fff4","ffe1")
                Protocol.FTMS -> (e.service=="1826" && (c in setOf("2acd","2ace","2ad1","2ad2","2ad3","2ada") || (c=="2ad9" && !MobiProfiles.officialFtms(name)))) ||
                    (e.service=="180d" && c=="2a37") || (e.service=="180f" && c=="2a19")
                else -> e.service=="180d" && c=="2a37"
            }
            if(read && e.properties and 2!=0) add(GattStep(GattAction.READ,e))
            if(notify && e.properties and (16 or 32)!=0) add(GattStep(GattAction.NOTIFY,e,
                if(protocol==Protocol.V1 && c=="ffe4" && name.startsWith("MB-HW")) 2000 else 0))
        }
    }
}
