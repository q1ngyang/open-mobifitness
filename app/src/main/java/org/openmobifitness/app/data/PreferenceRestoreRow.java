package org.openmobifitness.app.data;
import androidx.annotation.NonNull;
import androidx.room.*;
/** Durable commit journal: completed record transaction, pending preference commit. */
@Entity(tableName="preference_restore")
public class PreferenceRestoreRow {
    @PrimaryKey public int id=1;
    @NonNull public String json="";
}
