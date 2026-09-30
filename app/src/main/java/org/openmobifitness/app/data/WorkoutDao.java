package org.openmobifitness.app.data;
import androidx.room.*;
import java.util.List;
@Dao public interface WorkoutDao {
 @Query("SELECT * FROM sessions ORDER BY start DESC") List<SessionRow> sessions();
 @Query("SELECT * FROM sessions WHERE id=:id") SessionRow session(String id);
 @Query("SELECT * FROM samples ORDER BY sessionId,elapsedMs") List<SampleRow> samples();
 @Query("SELECT * FROM samples WHERE sessionId=:id ORDER BY elapsedMs") List<SampleRow> samplesFor(String id);
 @Query("SELECT * FROM workouts ORDER BY id") List<WorkoutRow> workouts();
 @Upsert void session(SessionRow row);
 @Insert(onConflict=OnConflictStrategy.ABORT) void sample(SampleRow row);
 @Upsert void workout(WorkoutRow row);
 @Query("DELETE FROM sessions WHERE id=:id") void deleteSession(String id);
 @Query("DELETE FROM workouts WHERE id=:id") void deleteWorkout(String id);
}
