package com.geeksville.mesh.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.dao.QuickChatActionDao
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction

@Database(
    entities = [
        MyNodeInfo::class,
        NodeInfo::class,
        NodeEntity::class,
        Packet::class,
        ContactSettings::class,
        MeshLog::class,
        QuickChatAction::class
    ],
    autoMigrations = [
        AutoMigration (from = 3, to = 4),
        AutoMigration (from = 4, to = 5),
        AutoMigration (from = 5, to = 6),
        AutoMigration (from = 6, to = 7),
        AutoMigration (from = 7, to = 8),
        AutoMigration (from = 8, to = 9),
        AutoMigration (from = 9, to = 10),
        AutoMigration (from = 10, to = 11),
        AutoMigration (from = 11, to = 12),
    ],
    version = 12,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun nodeInfoDao(): NodeInfoDao
    abstract fun packetDao(): PacketDao
    abstract fun meshLogDao(): MeshLogDao
    abstract fun quickChatActionDao(): QuickChatActionDao

    companion object {

        // Adds sendPackets column for statistics purposes
        val MIGRATION_9_10 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE NodeInfo ADD COLUMN sendPackets INTEGER DEFAULT 0 NOT NULL")
            }
        }

        fun getDatabase(context: Context): MeshtasticDatabase {

            return Room.databaseBuilder(
                context.applicationContext,
                MeshtasticDatabase::class.java,
                "meshtastic_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
