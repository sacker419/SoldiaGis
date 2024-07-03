package com.example.soldiagis

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(
    context: Context,
    name: String?,
    factory: SQLiteDatabase.CursorFactory?,
    version: Int
): SQLiteOpenHelper(context, name, factory, version){
    private val tableName = "server"

    override fun onCreate(db: SQLiteDatabase) {
        var sql = """
        CREATE TABLE IF NOT EXISTS $tableName (
            id integer primary key autoincrement,
            serverIP TEXT,
            serverPORT integer,
            tcpPORT integer
        );
    """.trimIndent()

        db.execSQL(sql)

        val cursor = db.rawQuery("SELECT * FROM $tableName", null)
        if (cursor.count == 0) {
            val initialValues = ContentValues()
            initialValues.put("serverIP", "127.0.0.1")
            initialValues.put("serverPORT", 5002)
            initialValues.put("tcpPORT", 9001)

            db.insert(tableName, null, initialValues)
        }
        cursor.close()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val sql = "DROP TABLE IF EXISTS $tableName"
        db.execSQL(sql)
        onCreate(db)
    }

    fun getServerData(): Cursor? {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $tableName WHERE id = 1", null)
    }
}