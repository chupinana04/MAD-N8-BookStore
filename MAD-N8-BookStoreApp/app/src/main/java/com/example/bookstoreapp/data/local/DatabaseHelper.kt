package com.example.bookstoreapp.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    
    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "BookStore.db"
        private const val TABLE_USER_SESSION = "user_session"
        private const val COLUMN_ID = "id"
        private const val COLUMN_IS_LOGGED_IN = "is_logged_in"
        private const val COLUMN_USER_ID = "user_id"
        private const val COLUMN_TOKEN = "token"
        private const val COLUMN_CUSTOMER_ID = "customer_id"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_USER_SESSION("
                + "$COLUMN_ID INTEGER PRIMARY KEY,"
                + "$COLUMN_IS_LOGGED_IN INTEGER,"
                + "$COLUMN_USER_ID TEXT,"
                + "$COLUMN_TOKEN TEXT,"
                + "$COLUMN_CUSTOMER_ID INTEGER" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USER_SESSION")
        onCreate(db)
    }

    fun setLoginData(isLoggedIn: Boolean, userId: String = "", token: String = "", customerId: Int = -1) {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM $TABLE_USER_SESSION")
        
        val values = ContentValues().apply {
            put(COLUMN_ID, 1)
            put(COLUMN_IS_LOGGED_IN, if (isLoggedIn) 1 else 0)
            put(COLUMN_USER_ID, userId)
            put(COLUMN_TOKEN, token)
            put(COLUMN_CUSTOMER_ID, customerId)
        }
        db.insert(TABLE_USER_SESSION, null, values)
        db.close()
    }

    fun setLoginStatus(isLoggedIn: Boolean, userId: String = "") {
        setLoginData(isLoggedIn, userId)
    }

    fun isLoggedIn(): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_USER_SESSION WHERE $COLUMN_ID = 1", null)
        var status = false
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(COLUMN_IS_LOGGED_IN)
            if (idx != -1) status = cursor.getInt(idx) == 1
        }
        cursor.close()
        db.close()
        return status
    }

    fun getToken(): String? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_TOKEN FROM $TABLE_USER_SESSION WHERE $COLUMN_ID = 1", null)
        var token: String? = null
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(COLUMN_TOKEN)
            if (idx != -1) token = cursor.getString(idx)
        }
        cursor.close()
        db.close()
        return token
    }

    fun getCustomerId(): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_CUSTOMER_ID FROM $TABLE_USER_SESSION WHERE $COLUMN_ID = 1", null)
        var id = -1
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(COLUMN_CUSTOMER_ID)
            if (idx != -1) id = cursor.getInt(idx)
        }
        cursor.close()
        db.close()
        return id
    }
}
