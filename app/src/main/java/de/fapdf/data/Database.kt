package de.fapdf.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Ein Dokument der App-Bibliothek. Die Datei liegt im internen App-Speicher. */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val filePath: String,
    /** Ursprüngliche URI (falls mit dauerhafter Schreibberechtigung geöffnet), sonst null. */
    val sourceUri: String? = null,
    val importedAt: Long,
    val modifiedAt: Long,
    val lastOpenedAt: Long,
    val pageCount: Int,
    val sizeBytes: Long,
    val favorite: Boolean = false,
    val thumbnailPath: String? = null,
    val encrypted: Boolean = false,
)

/** Personenprofil – alle persönlichen Werte liegen ausschließlich verschlüsselt in [encryptedData]. */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nur die Bezeichnung (z. B. „Ehepartner“) bleibt unverschlüsselt für die Listenansicht. */
    val label: String,
    val encryptedData: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean = other is ProfileEntity && other.id == id && other.updatedAt == updatedAt
    override fun hashCode(): Int = id.hashCode() * 31 + updatedAt.hashCode()
}

@Entity(tableName = "signatures", indices = [Index("profileId")])
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val profileId: Long? = null,
    /** Dateiname der verschlüsselten PNG-Datei im Ordner signatures/. */
    val fileName: String,
    val widthPx: Int,
    val heightPx: Int,
    val createdAt: Long,
)

@Entity(tableName = "bookmarks", indices = [Index("documentId")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val label: String,
    val createdAt: Long,
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProfileEntity): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SignatureEntity>>

    @Query("SELECT * FROM signatures WHERE id = :id")
    suspend fun get(id: Long): SignatureEntity?

    @Insert
    suspend fun insert(s: SignatureEntity): Long

    @Update
    suspend fun update(s: SignatureEntity)

    @Delete
    suspend fun delete(s: SignatureEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :docId ORDER BY pageIndex ASC")
    fun observeFor(docId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(b: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE documentId = :docId")
    suspend fun deleteFor(docId: Long)
}

@Database(
    entities = [DocumentEntity::class, ProfileEntity::class, SignatureEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun profileDao(): ProfileDao
    abstract fun signatureDao(): SignatureDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fapdf.db").build()
    }
}
