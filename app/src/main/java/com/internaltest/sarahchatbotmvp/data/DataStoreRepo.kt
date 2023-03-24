package com.internaltest.sarahchatbotmvp.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.internaltest.sarahchatbotmvp.data.Constants.USER_PREFERENCES
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(USER_PREFERENCES)

class DataStoreRepo (context: Context){

    private val dataStore = context.dataStore

    private var lastCreditDate : LocalDateTime? = null

    companion object {
        val SUBSCRIPTION_STATUS = stringPreferencesKey("subscription_status")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DAILY_LOGIN_DONE = booleanPreferencesKey("daily_login_done")
        val DAILY_LOGIN_DAY = stringPreferencesKey("daily_login_day")
        val CREDITS = intPreferencesKey("credits")
        val LAST_CREDIT_DATE = stringPreferencesKey("last_credit_date")
        val LAST_CREDIT_DATE_UPDATED = booleanPreferencesKey("last_credit_date_updated")
    }

    suspend fun setSubscriptionStatus(newStatus: String) {
        dataStore.edit { preferences ->
            preferences[SUBSCRIPTION_STATUS] = newStatus
        }
    }
    val getSubscriptionStatus: Flow<String> = dataStore.data.map {
        it[SUBSCRIPTION_STATUS] ?: "NENHUMA"
    }

    suspend fun setCredits(newCredits: Int) {
        dataStore.edit { preferences ->
            preferences[CREDITS] = newCredits
        }
    }

    val getCredits: Flow<Int> = dataStore.data.map {
        it[CREDITS] ?: 0
    }

    suspend fun setDarkMode(newStatus: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE] = newStatus
        }
    }

    @OptIn(FlowPreview::class)
    val getDarkMode: Flow<Boolean> = dataStore.data
        .debounce(500)  // delay of 500 milliseconds
        .map {
            it[DARK_MODE] ?: false
        }
    //TODO checar se usuário está com horário automático
    fun scheduleMonthlyCredits(newCredits: Int) {

        runBlocking {
            val subscriptionType = dataStore.data
                .map { preferences -> preferences[SUBSCRIPTION_STATUS] }.firstOrNull()

            val currentCredits = dataStore.data
                .map { preferences -> preferences[CREDITS] }.firstOrNull()

            val lastCreditDateStr = dataStore.data
                .map { preferences -> preferences[LAST_CREDIT_DATE] }.firstOrNull()

            val lastCreditDateBol = dataStore.data
            .map { preferences -> preferences[LAST_CREDIT_DATE_UPDATED] }.firstOrNull()

            if (subscriptionType == "LITE" && currentCredits != null ) {
                //Caso cliente nunca tenha feito inscrição lite, isso vai dar para ele créditos
                //assim que assinar
                if (lastCreditDateStr == null || lastCreditDateStr == ""){
                    Log.i("null lite credits",lastCreditDateStr.toString())
                    dataStore.edit { preferences ->
                        preferences[CREDITS] = currentCredits + newCredits
                    }
                }
                if (lastCreditDateBol == true && lastCreditDateStr != null) {
                    Log.i("lastCreditDateBol parse",lastCreditDateBol.toString())
                    Log.i("lastCreditDateStr parse",lastCreditDateStr.toString())
                    lastCreditDate = LocalDateTime.parse(lastCreditDateStr)
                } else {
                    Log.i("lastCreditDateBol now",lastCreditDateBol.toString())
                    Log.i("lastCreditDateStr now",lastCreditDateStr.toString())
                    dataStore.edit { preferences ->
                        preferences[LAST_CREDIT_DATE_UPDATED] = true
                        lastCreditDate = LocalDateTime.now()
                        preferences[LAST_CREDIT_DATE] = lastCreditDate.toString()
                    }
                }
                val nextCreditDate = lastCreditDate!!.plusMonths(1)
                //val nextCreditDateTest = lastCreditDate!!.plusMinutes(5)
                Log.i("lastcreditdate",lastCreditDate.toString())
                Log.i("nextCreditDate",nextCreditDate.toString())
                if (lastCreditDateBol == true && LocalDateTime.now().isAfter(nextCreditDate)) {
                    dataStore.edit { preferences ->
                        preferences[CREDITS] = currentCredits + newCredits
                        preferences[LAST_CREDIT_DATE] = nextCreditDate.toString()
                        preferences[LAST_CREDIT_DATE_UPDATED] = false
                        Log.i("nextcreditdate updated",nextCreditDate.toString())
                    }
                }
            }
        }
    }



    suspend fun setDailyLoginReward(todaystring: String, value: Boolean) {
        dataStore.edit { preferences ->
            preferences[DAILY_LOGIN_DONE] = value
            preferences[DAILY_LOGIN_DAY] = todaystring
        }
    }

    fun getDailyLoginReward(): Flow<Boolean> {
        return dataStore.data.map{it[DAILY_LOGIN_DONE] ?: false}
    }

}