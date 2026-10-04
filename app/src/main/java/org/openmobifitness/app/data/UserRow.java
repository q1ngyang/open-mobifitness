package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="users")
public class UserRow {
 @PrimaryKey @NonNull public String id="";
 @NonNull public String name="";
 public Double weightKg;
 @ColumnInfo(defaultValue="5.0") public double met=5.0;
 @NonNull @ColumnInfo(defaultValue="'default'") public String metSource="default";
 @NonNull @ColumnInfo(defaultValue="''") public String avatar="";
 @ColumnInfo(defaultValue="0") public long createdAt;
 public Long removedAt;
 @ColumnInfo(defaultValue="0") public boolean deleted;
 @NonNull @ColumnInfo(defaultValue="''") public String pendingPreferences="";
 @NonNull @ColumnInfo(defaultValue="''") public String legacyHints="";
}
