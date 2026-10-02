package com.muir.bear.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class WorkoutSummary(
    @Embedded val workout: Workout,
    val setCount: Int,
    val volume: Double?,
)

data class SetWithTime(
    @Embedded val set: WorkoutSet,
    val startedAt: Long,
    val workoutName: String,
)

data class GearWithWear(
    @Embedded val gear: Gear,
    val sessions: Int,
)

@Dao
interface TrainingDao {

    // ---------- Exercises ----------
    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun exercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    suspend fun allExercises(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun exercise(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun exerciseFlow(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun exerciseByName(name: String): Exercise?

    @Insert
    suspend fun insertExercise(e: Exercise): Long

    @Update
    suspend fun updateExercise(e: Exercise)

    @Query(
        "SELECT e.* FROM exercise_alt a JOIN exercise e ON e.id = a.altExerciseId " +
            "WHERE a.exerciseId = :exerciseId ORDER BY a.priority"
    )
    suspend fun alternatives(exerciseId: Long): List<Exercise>

    @Query("SELECT * FROM exercise_alt")
    suspend fun allAlts(): List<ExerciseAlt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlt(a: ExerciseAlt)

    @Query("DELETE FROM exercise_alt WHERE exerciseId = :exerciseId AND altExerciseId = :altId")
    suspend fun deleteAlt(exerciseId: Long, altId: Long)

    // ---------- Templates ----------
    @Query("SELECT * FROM template ORDER BY sortOrder, id")
    fun templates(): Flow<List<Template>>

    @Query("SELECT * FROM template ORDER BY sortOrder, id")
    suspend fun allTemplates(): List<Template>

    @Query("SELECT * FROM template WHERE id = :id")
    suspend fun template(id: Long): Template?

    @Insert
    suspend fun insertTemplate(t: Template): Long

    @Update
    suspend fun updateTemplate(t: Template)

    @Query("DELETE FROM template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("SELECT * FROM template_exercise WHERE templateId = :templateId ORDER BY position")
    fun templateExercises(templateId: Long): Flow<List<TemplateExercise>>

    @Query("SELECT * FROM template_exercise WHERE templateId = :templateId ORDER BY position")
    suspend fun templateExercisesList(templateId: Long): List<TemplateExercise>

    @Query("SELECT * FROM template_exercise")
    suspend fun allTemplateExercises(): List<TemplateExercise>

    @Insert
    suspend fun insertTemplateExercise(te: TemplateExercise): Long

    @Update
    suspend fun updateTemplateExercise(te: TemplateExercise)

    @Query("DELETE FROM template_exercise WHERE id = :id")
    suspend fun deleteTemplateExercise(id: Long)

    @Query("DELETE FROM template_exercise WHERE templateId = :templateId")
    suspend fun deleteTemplateExercises(templateId: Long)

    // ---------- Workouts ----------
    @Query("SELECT * FROM workout WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun activeWorkout(): Flow<Workout?>

    @Query("SELECT * FROM workout WHERE id = :id")
    fun workoutFlow(id: Long): Flow<Workout?>

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun workout(id: Long): Workout?

    @Query("SELECT * FROM workout WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun finishedWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workout ORDER BY startedAt")
    suspend fun allWorkouts(): List<Workout>

    @Query(
        "SELECT w.*, " +
            "(SELECT COUNT(*) FROM workout_set s WHERE s.workoutId = w.id AND s.completed = 1) AS setCount, " +
            "(SELECT SUM(COALESCE(s.weightKg, 0) * COALESCE(s.reps, 0)) FROM workout_set s " +
            "  WHERE s.workoutId = w.id AND s.completed = 1) AS volume " +
            "FROM workout w WHERE w.endedAt IS NOT NULL ORDER BY w.startedAt DESC"
    )
    fun workoutSummaries(): Flow<List<WorkoutSummary>>

    @Query("SELECT COUNT(*) FROM workout WHERE startedAt = :startedAt AND name = :name")
    suspend fun countWorkoutsAt(startedAt: Long, name: String): Int

    @Insert
    suspend fun insertWorkout(w: Workout): Long

    @Update
    suspend fun updateWorkout(w: Workout)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkoutRow(id: Long)

    @Query("DELETE FROM workout_set WHERE workoutId = :workoutId")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    // ---------- Sets ----------
    @Query("SELECT * FROM workout_set WHERE workoutId = :workoutId ORDER BY exerciseOrder, setIndex")
    fun setsFlow(workoutId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_set WHERE workoutId = :workoutId ORDER BY exerciseOrder, setIndex")
    suspend fun sets(workoutId: Long): List<WorkoutSet>

    @Query("SELECT * FROM workout_set")
    suspend fun allSets(): List<WorkoutSet>

    @Insert
    suspend fun insertSet(s: WorkoutSet): Long

    @Insert
    suspend fun insertSets(s: List<WorkoutSet>)

    @Update
    suspend fun updateSet(s: WorkoutSet)

    @Delete
    suspend fun deleteSet(s: WorkoutSet)

    @Query("DELETE FROM workout_set WHERE workoutId = :workoutId AND completed = 0")
    suspend fun deleteIncompleteSets(workoutId: Long)

    /** Completed sets of an exercise from finished workouts other than [excludeWorkoutId], newest first. */
    @Query(
        "SELECT s.*, w.startedAt AS startedAt, w.name AS workoutName FROM workout_set s " +
            "JOIN workout w ON w.id = s.workoutId " +
            "WHERE s.exerciseId = :exerciseId AND s.completed = 1 AND w.endedAt IS NOT NULL " +
            "AND w.id != :excludeWorkoutId ORDER BY w.startedAt DESC, s.setIndex"
    )
    suspend fun history(exerciseId: Long, excludeWorkoutId: Long): List<SetWithTime>

    @Query(
        "SELECT s.*, w.startedAt AS startedAt, w.name AS workoutName FROM workout_set s " +
            "JOIN workout w ON w.id = s.workoutId " +
            "WHERE s.exerciseId = :exerciseId AND s.completed = 1 AND w.endedAt IS NOT NULL " +
            "ORDER BY w.startedAt DESC, s.setIndex"
    )
    fun historyFlow(exerciseId: Long): Flow<List<SetWithTime>>

    @Query(
        "SELECT s.*, w.startedAt AS startedAt, w.name AS workoutName FROM workout_set s " +
            "JOIN workout w ON w.id = s.workoutId " +
            "WHERE s.completed = 1 AND w.endedAt IS NOT NULL ORDER BY w.startedAt"
    )
    fun allCompletedSets(): Flow<List<SetWithTime>>

    // ---------- Activities (basketball, conditioning, NEAT) ----------
    @Query("SELECT * FROM activity ORDER BY startedAt DESC")
    fun activities(): Flow<List<Activity>>

    @Query("SELECT * FROM activity ORDER BY startedAt")
    suspend fun allActivities(): List<Activity>

    @Insert
    suspend fun insertActivity(a: Activity): Long

    @Query("DELETE FROM activity WHERE id = :id")
    suspend fun deleteActivity(id: Long)

    // ---------- Sauna ----------
    @Query("SELECT * FROM sauna ORDER BY at DESC")
    fun saunas(): Flow<List<SaunaSession>>

    @Query("SELECT * FROM sauna ORDER BY at")
    suspend fun allSaunas(): List<SaunaSession>

    @Insert
    suspend fun insertSauna(s: SaunaSession): Long

    @Query("DELETE FROM sauna WHERE id = :id")
    suspend fun deleteSauna(id: Long)

    // ---------- Gear ----------
    @Query(
        "SELECT g.*, (SELECT COUNT(*) FROM gear_usage u WHERE u.gearId = g.id) AS sessions " +
            "FROM gear g ORDER BY g.retired, g.category, g.name"
    )
    fun gearWithWear(): Flow<List<GearWithWear>>

    @Query("SELECT * FROM gear")
    suspend fun allGear(): List<Gear>

    @Query("SELECT * FROM gear_usage")
    suspend fun allGearUsage(): List<GearUsage>

    @Insert
    suspend fun insertGear(g: Gear): Long

    @Update
    suspend fun updateGear(g: Gear)

    @Query("DELETE FROM gear WHERE id = :id")
    suspend fun deleteGearRow(id: Long)

    @Query("DELETE FROM gear_usage WHERE gearId = :gearId")
    suspend fun deleteGearUsageForGear(gearId: Long)

    @Insert
    suspend fun insertGearUsage(u: GearUsage)

    @Query("DELETE FROM gear_usage WHERE sessionType = :sessionType AND sessionId = :sessionId")
    suspend fun deleteGearUsageForSession(sessionType: String, sessionId: Long)

    // ---------- Sleep ----------
    @Query("SELECT * FROM sleep ORDER BY bedAt DESC")
    fun sleeps(): Flow<List<Sleep>>

    @Query("SELECT * FROM sleep ORDER BY bedAt")
    suspend fun allSleeps(): List<Sleep>

    @Insert
    suspend fun insertSleep(s: Sleep): Long

    @Update
    suspend fun updateSleep(s: Sleep)

    /** Deletes a night and its per-minute samples. */
    @Query("DELETE FROM sleep WHERE id = :id")
    suspend fun deleteSleepRow(id: Long)

    @Query("DELETE FROM sleep_sample WHERE sleepId = :sleepId")
    suspend fun deleteSleepSamples(sleepId: Long)

    @Query("SELECT * FROM sleep WHERE id = :id")
    suspend fun sleep(id: Long): Sleep?

    @Query("SELECT * FROM sleep WHERE id = :id")
    fun sleepFlow(id: Long): Flow<Sleep?>

    @Insert
    suspend fun insertSleepSample(s: SleepSample)

    @Query("SELECT * FROM sleep_sample WHERE sleepId = :sleepId ORDER BY at")
    suspend fun sleepSamples(sleepId: Long): List<SleepSample>

    @Query("SELECT * FROM sleep_sample WHERE sleepId = :sleepId ORDER BY at")
    fun sleepSamplesFlow(sleepId: Long): Flow<List<SleepSample>>

    // ---------- Steps ----------
    @Query("SELECT * FROM daily_steps ORDER BY day DESC")
    fun dailySteps(): Flow<List<DailySteps>>

    @Query("SELECT * FROM daily_steps WHERE day = :day")
    suspend fun stepsOn(day: Long): DailySteps?

    @Upsert
    suspend fun upsertSteps(s: DailySteps)

    // ---------- Check-in ----------
    @Query("SELECT * FROM checkin ORDER BY day DESC")
    fun checkIns(): Flow<List<CheckIn>>

    @Query("SELECT * FROM checkin ORDER BY day")
    suspend fun allCheckIns(): List<CheckIn>

    @Query("SELECT * FROM checkin WHERE day = :day")
    suspend fun checkIn(day: Long): CheckIn?

    @Upsert
    suspend fun upsertCheckIn(c: CheckIn)

    @Query("DELETE FROM checkin WHERE id = :id")
    suspend fun deleteCheckIn(id: Long)

    // ---------- Niggles ----------
    @Query("SELECT * FROM niggle ORDER BY at DESC")
    fun niggles(): Flow<List<Niggle>>

    @Query("SELECT * FROM niggle ORDER BY at")
    suspend fun allNiggles(): List<Niggle>

    @Insert
    suspend fun insertNiggle(n: Niggle): Long

    @Query("DELETE FROM niggle WHERE id = :id")
    suspend fun deleteNiggle(id: Long)

    // ---------- Bodyweight ----------
    @Query("SELECT * FROM bodyweight ORDER BY day DESC")
    fun bodyweights(): Flow<List<BodyWeight>>

    @Query("SELECT * FROM bodyweight ORDER BY day")
    suspend fun allBodyweights(): List<BodyWeight>

    @Query("SELECT * FROM bodyweight WHERE day = :day")
    suspend fun bodyweight(day: Long): BodyWeight?

    @Upsert
    suspend fun upsertBodyweight(b: BodyWeight)

    @Query("DELETE FROM bodyweight WHERE id = :id")
    suspend fun deleteBodyweight(id: Long)

    // ---------- Protein ----------
    @Query("SELECT * FROM protein ORDER BY at DESC")
    fun proteins(): Flow<List<ProteinEntry>>

    @Query("SELECT * FROM protein ORDER BY at")
    suspend fun allProteins(): List<ProteinEntry>

    @Insert
    suspend fun insertProtein(p: ProteinEntry): Long

    @Query("DELETE FROM protein WHERE id = :id")
    suspend fun deleteProtein(id: Long)

    @Query("SELECT * FROM protein_preset ORDER BY sortOrder, id")
    fun proteinPresets(): Flow<List<ProteinPreset>>

    @Query("SELECT * FROM protein_preset ORDER BY sortOrder, id")
    suspend fun allProteinPresets(): List<ProteinPreset>

    @Insert
    suspend fun insertProteinPreset(p: ProteinPreset): Long

    @Query("DELETE FROM protein_preset WHERE id = :id")
    suspend fun deleteProteinPreset(id: Long)

    // ---------- Supplements ----------
    @Query("SELECT * FROM supplement WHERE active = 1 ORDER BY sortOrder, id")
    fun supplements(): Flow<List<Supplement>>

    @Query("SELECT * FROM supplement ORDER BY sortOrder, id")
    suspend fun allSupplements(): List<Supplement>

    @Insert
    suspend fun insertSupplement(s: Supplement): Long

    @Update
    suspend fun updateSupplement(s: Supplement)

    @Query("SELECT * FROM supplement_log WHERE day = :day")
    fun supplementLog(day: Long): Flow<List<SupplementLog>>

    @Query("SELECT * FROM supplement_log")
    suspend fun allSupplementLogs(): List<SupplementLog>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSupplementLog(l: SupplementLog)

    @Delete
    suspend fun deleteSupplementLog(l: SupplementLog)

    // ---------- Bars ----------
    @Query("SELECT * FROM bar ORDER BY weightKg DESC")
    fun bars(): Flow<List<Bar>>

    @Query("SELECT * FROM bar ORDER BY weightKg DESC")
    suspend fun allBars(): List<Bar>

    @Insert
    suspend fun insertBar(b: Bar): Long

    @Update
    suspend fun updateBar(b: Bar)

    @Query("DELETE FROM bar WHERE id = :id")
    suspend fun deleteBar(id: Long)

    // ---------- Venues ----------
    @Query("SELECT * FROM venue ORDER BY isTravel, name")
    fun venues(): Flow<List<Venue>>

    @Query("SELECT * FROM venue ORDER BY isTravel, name")
    suspend fun allVenues(): List<Venue>

    @Query("SELECT * FROM venue WHERE id = :id")
    suspend fun venue(id: Long): Venue?

    @Insert
    suspend fun insertVenue(v: Venue): Long

    @Update
    suspend fun updateVenue(v: Venue)

    @Query("DELETE FROM venue WHERE id = :id")
    suspend fun deleteVenue(id: Long)

    // ---------- Jump tests ----------
    @Query("SELECT * FROM jump_test ORDER BY at DESC")
    fun jumpTests(): Flow<List<JumpTest>>

    @Query("SELECT * FROM jump_test ORDER BY at")
    suspend fun allJumpTests(): List<JumpTest>

    @Insert
    suspend fun insertJumpTest(j: JumpTest): Long

    @Query("DELETE FROM jump_test WHERE id = :id")
    suspend fun deleteJumpTest(id: Long)

    // ---------- Settings ----------
    @Query("SELECT value FROM setting WHERE `key` = :key")
    suspend fun setting(key: String): String?

    @Query("SELECT value FROM setting WHERE `key` = :key")
    fun settingFlow(key: String): Flow<String?>

    @Query("SELECT * FROM setting")
    suspend fun allSettings(): List<Setting>

    @Upsert
    suspend fun putSetting(s: Setting)

    @Query("DELETE FROM setting WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    // ---------- Bulk insert for restore ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreExercises(x: List<Exercise>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAlts(x: List<ExerciseAlt>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTemplates(x: List<Template>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTemplateExercises(x: List<TemplateExercise>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreWorkouts(x: List<Workout>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSets(x: List<WorkoutSet>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreActivities(x: List<Activity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSaunas(x: List<SaunaSession>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreGear(x: List<Gear>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreGearUsage(x: List<GearUsage>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSleeps(x: List<Sleep>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreCheckIns(x: List<CheckIn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreNiggles(x: List<Niggle>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreBodyweights(x: List<BodyWeight>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreProteins(x: List<ProteinEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreProteinPresets(x: List<ProteinPreset>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSupplements(x: List<Supplement>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSupplementLogs(x: List<SupplementLog>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreBars(x: List<Bar>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreVenues(x: List<Venue>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreJumpTests(x: List<JumpTest>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSettings(x: List<Setting>)

    // ---------- HRV ----------

    @Insert
    suspend fun insertHrv(r: HrvReading): Long

    @Query("SELECT * FROM hrv_reading ORDER BY at DESC")
    fun hrvReadings(): Flow<List<HrvReading>>

    @Query("SELECT * FROM hrv_reading ORDER BY at DESC")
    suspend fun allHrv(): List<HrvReading>

    @Query("DELETE FROM hrv_reading WHERE id = :id")
    suspend fun deleteHrv(id: Long)
}
