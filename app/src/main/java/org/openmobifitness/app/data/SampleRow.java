package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="samples", primaryKeys={"sessionId","elapsedMs"},
 foreignKeys=@ForeignKey(entity=SessionRow.class,parentColumns="id",childColumns="sessionId",onDelete=ForeignKey.CASCADE),
 indices=@Index("sessionId"))
public class SampleRow {
 @NonNull public String sessionId="";
 public long elapsedMs;
 public Double cadence, resistance, speedMps, distanceM, powerW;
 public Integer heartBpm, strokes;
}
