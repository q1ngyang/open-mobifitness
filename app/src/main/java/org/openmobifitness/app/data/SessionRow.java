package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="sessions", indices={@androidx.room.Index("startEpoch"),@androidx.room.Index(value={"archived","startEpoch"}),@androidx.room.Index(value={"machine","startEpoch"}),@androidx.room.Index(value={"ownerUserId","startEpoch"}),@androidx.room.Index("startedUserId")}, foreignKeys={@androidx.room.ForeignKey(entity=UserRow.class,parentColumns="id",childColumns="ownerUserId",onDelete=androidx.room.ForeignKey.RESTRICT),@androidx.room.ForeignKey(entity=UserRow.class,parentColumns="id",childColumns="startedUserId",onDelete=androidx.room.ForeignKey.RESTRICT)})
public class SessionRow {
 @PrimaryKey @NonNull public String id="";
 @NonNull public String start="", end="", zone="", device="", machine="", protocol="", status="";
 @androidx.room.ColumnInfo(defaultValue="0") public long startEpoch;
 @androidx.room.ColumnInfo(defaultValue="0") public boolean archived;
 @NonNull @androidx.room.ColumnInfo(defaultValue="''") public String workoutId="", workoutTitle="", energyModel="";
 public long elapsedMs;
 public Double distanceM;
 public boolean demo;
 public Double caloriesKcal, weightKg, met;
 @androidx.room.ColumnInfo(defaultValue="0") public boolean caloriesEstimated, distanceEstimated;
 @androidx.room.ColumnInfo(defaultValue="NULL") public String ownerUserId,startedUserId;
 @NonNull @androidx.room.ColumnInfo(defaultValue="''") public String startedUserName="",weightSource="",metSource="",workoutSnapshot="",capabilitySnapshot="",ownerHistory="",enteredStageIds="";
 @androidx.room.ColumnInfo(defaultValue="0") public int identityVersion;
}
