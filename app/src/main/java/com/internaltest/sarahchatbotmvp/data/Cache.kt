package com.internaltest.sarahchatbotmvp.data

class Cache<Key, Value> {
    private val cache: HashMap<Key, Value> = HashMap()

    fun put(key: Key, value: Value) {
        cache[key] = value
    }

    fun get(key: Key): Value? {
        return cache[key]
    }

    fun containsKey(key: Key): Boolean {
        return cache.containsKey(key)
    }
}
