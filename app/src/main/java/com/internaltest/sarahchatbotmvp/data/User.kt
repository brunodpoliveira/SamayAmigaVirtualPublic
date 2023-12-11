package com.internaltest.sarahchatbotmvp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_table")
data class User(
    @PrimaryKey
    var firebaseId: String,

    @ColumnInfo(name = "subscription_status")
    var subscriptionStatus: String,

    @ColumnInfo(name = "credits")
    var credits: Int,

    @ColumnInfo(name = "total_messages_sent")
    var totalMsgs: Int,

    @ColumnInfo(name = "dark_mode")
    var darkMode: Boolean,

    @ColumnInfo(name = "text_to_speech")
    var textToSpeech: Boolean,

    @ColumnInfo(name = "daily_login_day")
    var dailyLoginDay: String,

    @ColumnInfo(name = "font_size")
    var fontSize: Int,
){
    override fun toString(): String {
        return "User(firebaseId=$firebaseId, subscriptionStatus=$subscriptionStatus, credits=$credits, totalMsgs=$totalMsgs, darkMode=$darkMode, textToSpeech=$textToSpeech, dailyLoginDay=$dailyLoginDay, fontSize=$fontSize)"
    }
}
