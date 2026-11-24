package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FocusRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        null
                    } else {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                },
        )
    }

    fun observeSessions(userId: String): Flow<List<FocusSession>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FocusSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                },
        )
    }

    fun observeTasks(userId: String): Flow<List<FocusTask>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users").document(userId).collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FocusTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                },
        )
    }

    suspend fun ensureUserProfile(userId: String, initialDisplayName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = initialDisplayName.trim().ifBlank { "FocusPulse User" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "focusMinutes" to 25L,
                    "shortBreakMinutes" to 5L,
                    "longBreakMinutes" to 15L,
                    "dailyGoalMinutes" to 120L,
                    "hapticsEnabled" to true,
                    "autoStartBreak" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSettings(
        userId: String,
        displayName: String,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        dailyGoalMinutes: Int,
        hapticsEnabled: Boolean,
        autoStartBreak: Boolean,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val safeName = displayName.trim().ifBlank { "FocusPulse User" }.take(80)
            val safeFocus = focusMinutes.coerceIn(1, 120).toLong()
            val safeShort = shortBreakMinutes.coerceIn(1, 60).toLong()
            val safeLong = longBreakMinutes.coerceIn(1, 60).toLong()
            val safeGoal = dailyGoalMinutes.coerceIn(15, 720).toLong()

            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "focusMinutes" to safeFocus,
                        "shortBreakMinutes" to safeShort,
                        "longBreakMinutes" to safeLong,
                        "dailyGoalMinutes" to safeGoal,
                        "hapticsEnabled" to hapticsEnabled,
                        "autoStartBreak" to autoStartBreak,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "focusMinutes" to safeFocus,
                        "shortBreakMinutes" to safeShort,
                        "longBreakMinutes" to safeLong,
                        "dailyGoalMinutes" to safeGoal,
                        "hapticsEnabled" to hapticsEnabled,
                        "autoStartBreak" to autoStartBreak,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun logSession(
        userId: String,
        title: String,
        category: FocusCategory,
        durationMinutes: Int,
        sessionType: SessionType,
        dateKey: String,
        taskId: String?,
    ): Result<Unit> {
        var path = "users/$userId/sessions"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val safeTitle = title.trim().ifBlank { "자유 집중 세션" }.take(120)
            val safeMinutes = durationMinutes.coerceIn(1, 180).toLong()
            val validTaskId = taskId?.takeIf { it.isNotBlank() && it.length <= 128 && ID_REGEX.matches(it) }

            val docRef = db.collection("users").document(uid).collection("sessions").document()
            path = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "title" to safeTitle,
                "category" to category.name,
                "durationMinutes" to safeMinutes,
                "sessionType" to sessionType.name,
                "dateKey" to dateKey,
                "taskId" to validTaskId,
                "createdAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> {
        val path = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            require(sessionId.isNotBlank()) { "sessionId must not be blank" }
            db.collection("users").document(uid).collection("sessions").document(sessionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addTask(
        userId: String,
        title: String,
        category: FocusCategory,
        estimatedPomodoros: Int,
    ): Result<Unit> {
        var path = "users/$userId/tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Task title must not be blank" }
            val safeEstimated = estimatedPomodoros.coerceIn(1, 20).toLong()

            val docRef = db.collection("users").document(uid).collection("tasks").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "category" to category.name,
                "estimatedPomodoros" to safeEstimated,
                "completedPomodoros" to 0L,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        category: FocusCategory,
        estimatedPomodoros: Int,
    ): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(taskId.isNotBlank()) { "taskId must not be blank" }
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Task title must not be blank" }
            val safeEstimated = estimatedPomodoros.coerceIn(1, 20).toLong()

            db.collection("users").document(uid).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "title" to safeTitle,
                        "category" to category.name,
                        "estimatedPomodoros" to safeEstimated,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(
        userId: String,
        taskId: String,
        completed: Boolean,
    ): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(taskId.isNotBlank()) { "taskId must not be blank" }

            db.collection("users").document(uid).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementTaskPomodoro(userId: String, taskId: String): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(taskId.isNotBlank()) { "taskId must not be blank" }

            db.collection("users").document(uid).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "completedPomodoros" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            require(taskId.isNotBlank()) { "taskId must not be blank" }

            db.collection("users").document(uid).collection("tasks").document(taskId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private companion object {
        val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
