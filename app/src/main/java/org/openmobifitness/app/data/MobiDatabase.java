package org.openmobifitness.app.data;
import androidx.room.*;
@Database(entities={SessionRow.class,SampleRow.class,WorkoutRow.class,PreferenceRestoreRow.class},version=7,exportSchema=true)
public abstract class MobiDatabase extends RoomDatabase { public abstract WorkoutDao records(); }
