package org.openmobifitness.app.data;
import androidx.room.*;
@Database(entities={SessionRow.class,SampleRow.class,WorkoutRow.class,PreferenceRestoreRow.class,UserRow.class},version=9,exportSchema=true)
public abstract class MobiDatabase extends RoomDatabase { public abstract WorkoutDao records(); }
