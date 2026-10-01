package org.openmobifitness.app.data;
import androidx.room.*;
@Database(entities={SessionRow.class,SampleRow.class,WorkoutRow.class},version=2,exportSchema=true)
public abstract class MobiDatabase extends RoomDatabase { public abstract WorkoutDao records(); }
