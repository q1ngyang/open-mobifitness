package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="sessions", indices={@androidx.room.Index("startEpoch"),@androidx.room.Index(value={"archived","startEpoch"}),@androidx.room.Index(value={"machine","startEpoch"})})
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
}
