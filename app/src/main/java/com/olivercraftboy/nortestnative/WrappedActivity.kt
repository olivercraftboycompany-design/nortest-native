package com.olivercraftboy.nortestnative

import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import org.json.JSONObject

/* Wrapped: your listening story from the play history the player keeps. */
class WrappedActivity : android.app.Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<TextView>(R.id.dHeading).text = "Wrapped"
        findViewById<View>(R.id.dAction).visibility = View.GONE
        build()
    }

    private fun build() {
        val a = Store.listens(this)
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        var plays = 0
        var minutes = 0L
        val songs = HashMap<String, Int>()
        val songNames = HashMap<String, String>()
        val artists = HashMap<String, Int>()
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            val ts = e.optLong("ts", 0)
            val c = java.util.Calendar.getInstance()
            c.timeInMillis = ts
            if (c.get(java.util.Calendar.YEAR) != year) continue
            plays++
            minutes += e.optLong("d", 0) / 60000
            val title = e.optString("t", "")
            val artist = e.optString("a", "")
            if (title.isEmpty()) continue
            val k = title.lowercase() + "|" + artist.lowercase()
            songs[k] = (songs[k] ?: 0) + 1
            songNames[k] = title + "  ·  " + artist
            if (artist.isNotEmpty()) artists[artist] = (artists[artist] ?: 0) + 1
        }
        val rows = ArrayList<Map<String, String>>()
        if (plays == 0) {
            val m = HashMap<String, String>()
            m["t"] = "Nothing yet"
            m["a"] = "Play some songs and your Wrapped builds itself"
            rows.add(m)
        } else {
            add(rows, plays.toString() + " plays this year", "About " + minutes + " minutes of music")
            val topS = songs.entries.sortedByDescending { it.value }.take(10)
            var n = 1
            for (e in topS) {
                add(rows, n.toString() + ".  " + songNames[e.key], e.value.toString() + " plays")
                n++
            }
        }
        val list = findViewById<ListView>(R.id.dList)
        list.adapter = SimpleAdapter(this, rows, android.R.layout.simple_list_item_2,
            arrayOf("t", "a"), intArrayOf(android.R.id.text1, android.R.id.text2))
    }

    private fun add(rows: MutableList<Map<String, String>>, t: String, a: String) {
        val m = HashMap<String, String>()
        m["t"] = t; m["a"] = a
        rows.add(m)
    }
}
