package org.openmobifitness.app.data

import org.openmobifitness.core.*
import java.time.*
import java.util.UUID
import kotlin.math.sin

/** One immutable manifest. Keep v1 IDs available if a future dataset is introduced. */
internal object DebugDataset {
    const val VERSION=1
    val machines=listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL,Machine.JUMP_ROPE,Machine.DUMBBELL)
    fun id(key: String)=UUID.nameUUIDFromBytes("OpenMOBI/debug-samples/v1/$key".toByteArray(Charsets.UTF_8)).toString()
    val userIds=(0..3).map { id("user/$it") }
    data class Entry(val key: String,val user: Int?,val machine: Machine,val slot: Int,val dense: Boolean=false,val boundary: Int?=null) { val id get()=DebugDataset.id(key) }
    val entries=buildList {
        for(u in 0..2) for(m in machines) for(month in 0..35) add(Entry("base/$u/${m.name}/$month",u,m,month,dense=u==0 && month<2))
        for(m in machines) for(month in 0..11) add(Entry("removed/${m.name}/$month",3,m,month*3+1))
        for(m in machines) for(month in 0..5) add(Entry("unassigned/${m.name}/$month",null,m,month*6+2))
        for(u in 0..2) for(case in 0..7) add(Entry("boundary/$u/$case",u,machines[(u+case)%6],case,boundary=case))
    }
    val sessionIds=entries.map { it.id }.toSet()
    data class Fixture(val session: Session,val samples: List<Sample>)
    fun users(anchor: Instant,zone: ZoneId,names: List<String>)=userIds.mapIndexed { u,id ->
        UserProfile(id,names[u],weightKg=58.0+u*9,met=5.0,metSource="default",createdAt=YearMonth.from(anchor.atZone(zone)).minusMonths(36).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),removedAt=if(u==3) anchor.toEpochMilli() else null)
    }
    fun fixture(entry: Entry,anchor: Instant,zone: ZoneId,names: List<String>): Fixture {
        val u=entry.user ?: 4; val mi=machines.indexOf(entry.machine); val month=YearMonth.from(anchor.atZone(zone))
        val duration=if(entry.boundary==7) 12L else ((8+(u*5+mi*3+entry.slot)%22)*60).toLong()
        val base=month.minusMonths(36-entry.slot.toLong()).atDay(5+(u*3+mi)%20).atTime(9+u,mi*7).atZone(zone).toInstant()
        val local=anchor.atZone(zone)
        val proposed=when(entry.boundary) {
            0 -> LocalDate.of(local.year-1,12,31).atTime(23,10).atZone(zone).toInstant()
            1 -> LocalDate.of(local.year,1,1).atTime(8,0).atZone(zone).toInstant()
            2 -> month.atDay(1).minusDays(1).atTime(22,0).atZone(zone).toInstant()
            3 -> month.minusMonths(1).atDay(1).atTime(8,0).atZone(zone).toInstant()
            4,5 -> month.minusMonths(1).atDay(18).atTime(if(entry.boundary==4) 8 else 19,u*5).atZone(zone).toInstant()
            6,7 -> anchor.minusSeconds(duration+60+u*180L)
            else -> base
        }
        val start=if(proposed.plusSeconds(duration)>anchor) anchor.minusSeconds(duration+60) else proposed
        val end=start.plusSeconds(duration)
        val m=entry.machine
        val speed=when(m) { Machine.BIKE -> 4.0; Machine.ELLIPTICAL -> 1.2; Machine.ROWER -> 2.1; Machine.TREADMILL -> 2.3; else -> null }?.plus(u*.09+entry.slot*.006)
        val distance=speed?.times(duration)
        val calories=duration/60.0*(3.5+u*.6+mi*.2)
        val estimated=(entry.slot+u)%4==0
        val session=Session(id=entry.id,start=start.toString(),end=end.toString(),zone=zone.id,device="Debug sample v1 / ${m.name} / ${entry.key}",machine=m,protocol=Protocol.DEMO,
            elapsedMs=duration*1000,distanceM=distance,demo=true,status=if(entry.boundary==7 || entry.slot%11==7) "interrupted" else "completed",caloriesKcal=calories,caloriesEstimated=estimated,distanceEstimated=estimated && m==Machine.ELLIPTICAL,
            weightKg=58.0+u*9,met=5.0,energyModel=if(estimated) "met" else "",ownerUserId=entry.user?.let(userIds::get),startedUserId=entry.user?.let(userIds::get),startedUserName=entry.user?.let(names::get).orEmpty(),weightSource=if(entry.user==null) "legacy" else "user",metSource="default",identityVersion=if(entry.user==null) 0 else 1,
            capabilitySnapshot="{\"version\":1,\"machine\":\"${m.name}\",\"protocol\":\"DEMO\",\"model\":\"Debug sample\",\"evidence\":\"offline-v1\",\"resistanceWrite\":\"unsupported\",\"speedWrite\":\"unsupported\",\"inclineWrite\":\"unsupported\"}")
        val times=if(entry.dense) (0..duration).map { it*1000 } else (0..59).map { it*duration*1000/59 }.distinct()
        val samples=times.mapIndexed { i,ms ->
            val fraction=ms/(duration*1000.0); val wave=sin(fraction*Math.PI*4)*3
            val gap=i in times.size/3 until times.size/3+maxOf(1,times.size/24)
            val heart=if((u+entry.slot)%5==0 || gap) null else (105+u*4+mi*3+wave).toInt()
            val common=Metrics(heartBpm=heart,caloriesKcal=calories*fraction,deviceDurationSec=(ms/1000).toInt())
            val metrics=when(m) {
                Machine.ELLIPTICAL,Machine.BIKE -> common.copy(cadence=if(gap) null else 48+u*4+entry.slot*.15+wave,resistance=if(gap) null else (3+u+entry.slot%8).toDouble(),speedMps=if(gap) null else speed,distanceM=distance!!*fraction,powerW=if(gap) null else 65+u*12+wave*2,powerEstimated=estimated)
                Machine.ROWER -> common.copy(cadence=if(gap) null else 21+u+wave/3,speedMps=if(gap) null else speed,distanceM=distance!!*fraction,strokes=(duration*(21+u)/60.0*fraction).toInt(),forceN=if(gap) null else 145+u*12+wave,powerW=if(gap) null else 90+u*10+wave)
                Machine.TREADMILL -> common.copy(speedMps=if(gap) null else speed,distanceM=distance!!*fraction,stepRate=if(gap) null else 145+u*5+wave,stepCount=(duration*(145+u*5)/60.0*fraction).toInt(),inclinePercent=if(gap) null else (entry.slot%5).toDouble(),strideM=if(gap) null else .75+u*.03)
                Machine.JUMP_ROPE -> common.copy(cadence=if(gap) null else 95+u*7+wave,jumpCount=(duration*(95+u*7)/60.0*fraction).toInt(),continuousJumps=(120+u*20).coerceAtMost((duration*(95+u*7)/60.0*fraction).toInt()),jumpInterruptions=(fraction*(2+entry.slot%4)).toInt())
                Machine.DUMBBELL -> common.copy(repetitions=(duration/12*fraction).toInt(),loadKg=if(gap || entry.slot%4==1) null else 4.0+u*2)
                else -> common
            }
            Sample(session.id,ms,metrics)
        }
        return Fixture(session,samples)
    }
}
