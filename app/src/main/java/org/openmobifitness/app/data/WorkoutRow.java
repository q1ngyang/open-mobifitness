package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="workouts")
public class WorkoutRow {
 @PrimaryKey @NonNull public String id="";
 @NonNull public String csv="";
}
