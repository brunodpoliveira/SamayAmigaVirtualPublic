package com.internaltest.sarahchatbotmvp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Transaction
    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM user_table")
    fun getAllData(): LiveData<List<User>>

    @Query("SELECT * from user_table WHERE firebaseId = :id")
    suspend fun getUser(id: String?): User?

    @Query("SELECT font_size FROM user_table WHERE firebaseId = :userId")
    fun getFontSizeLiveData(userId: String): LiveData<Int>

    @Query("SELECT text_to_speech FROM user_table WHERE firebaseId = :userId")
    fun getTextToSpeechLiveData(userId: String): LiveData<Boolean>

    @Query("SELECT total_messages_sent FROM user_table WHERE firebaseId = :userId")
    fun getTotalMsgsLiveData(userId: String): LiveData<Int>

    @Query("SELECT dark_mode FROM user_table WHERE firebaseId = :userId")
    fun getDarkModeData(userId: String): LiveData<Boolean>

    @Query("SELECT daily_login_day FROM user_table WHERE firebaseId = :userId")
    fun getDailyLoginData(userId: String): LiveData<String>
}
