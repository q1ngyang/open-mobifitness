package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="workouts",indices={@Index("ownerUserId")},foreignKeys={@ForeignKey(entity=UserRow.class,parentColumns="id",childColumns="ownerUserId",onDelete=ForeignKey.RESTRICT)})
public class WorkoutRow {
 @PrimaryKey @NonNull public String id="";
 @NonNull public String csv="";
 @ColumnInfo(defaultValue="NULL") public String ownerUserId;
}
