package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="sessions")
public class SessionRow {
 @PrimaryKey @NonNull public String id="";
 @NonNull public String start="", end="", zone="", device="", machine="", protocol="", status="";
 public long elapsedMs;
 public Double distanceM;
 public boolean demo;
 public Double caloriesKcal, weightKg, met;
 @androidx.room.ColumnInfo(defaultValue="0") public boolean caloriesEstimated, distanceEstimated;
}
