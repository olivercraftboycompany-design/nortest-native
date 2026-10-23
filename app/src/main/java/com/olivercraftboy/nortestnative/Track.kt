package com.olivercraftboy.nortestnative

import org.json.JSONObject

/* One playable song, either from this phone (localId set) or a streaming/imported one
   (vid set, the YouTube video the player streams or downloads). */
class Track(val ref: String, val title: String, val artist: String, val dur: Long, val vid: String?, val localId: Long?) {

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("ref", ref); o.put("t", title); o.put("a", artist)
        o.put("d", dur)
        vid?.let { o.put("v", it) }
        localId?.let { o.put("lid", it) }
        return o
    }

    companion object {
        fun local(id: Long, title: String, artist: String, dur: Long) =
            Track("L:$id", title, artist, dur, null, id)

        fun yt(vid: String, title: String, artist: String, dur: Long) =
            Track("Y:$vid", title, artist, dur, vid, null)

        fun fromJson(o: JSONObject): Track? {
            return try {
                val ref = o.getString("ref")
                if (ref.startsWith("Y:")) {
                    val vid = ref.substring(2)
                    Track(ref, o.optString("t", "Untitled"), o.optString("a", ""), o.optLong("d", 0), vid, null)
                } else Track(ref, o.optString("t", "Untitled"), o.optString("a", ""), o.optLong("d", 0), o.optString("v", "").ifEmpty { null }, if (o.has("lid")) o.getLong("lid") else null)
            } catch (e: Exception) { null }
        }
    }
}
