package com.nexora.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val pkg: String, val label: String)

/**
 * Default: the icons the system provides (on a Samsung phone these are the
 * phone's own One UI icons). Optional: an installed icon pack (appfilter.xml format).
 */
class IconProvider(context: Context) {
    private val pm = context.packageManager
    private val sizePx = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    private val cache = LruCache<String, ImageBitmap>(120)

    private class PackState(val pkg: String?, val res: Resources?, val map: Map<String, String>)

    @Volatile
    private var state = PackState(null, null, emptyMap())

    fun installedPacks(): List<IconPackInfo> {
        val seen = HashSet<String>()
        val out = ArrayList<IconPackInfo>()
        for (action in listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")) {
            for (ri in pm.queryIntentActivities(Intent(action), 0)) {
                val p = ri.activityInfo.packageName
                if (seen.add(p)) out += IconPackInfo(p, ri.loadLabel(pm).toString())
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    /** Call off the main thread: parses the pack's appfilter.xml. */
    fun setPack(pkg: String?) {
        state = if (pkg == null) PackState(null, null, emptyMap()) else loadPack(pkg)
        cache.evictAll()
    }

    fun icon(app: AppInfo): ImageBitmap {
        val s = state
        val key = "${s.pkg}|${app.key}"
        cache.get(key)?.let { return it }
        val drawable = packDrawable(s, app) ?: app.info.getBadgedIcon(0)
        return drawable.toBitmap(sizePx).asImageBitmap().also { cache.put(key, it) }
    }

    private fun packDrawable(s: PackState, app: AppInfo): Drawable? {
        val res = s.res ?: return null
        val pack = s.pkg ?: return null
        val name = s.map["${app.pkg}/${app.cls}"] ?: return null
        return try {
            val id = res.getIdentifier(name, "drawable", pack)
            if (id == 0) null else res.getDrawable(id, null)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPack(pack: String): PackState {
        val map = HashMap<String, String>()
        return try {
            val res = pm.getResourcesForApplication(pack)
            val resId = res.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser = if (resId != 0) {
                res.getXml(resId)
            } else {
                Xml.newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), "UTF-8") }
            }
            val regex = Regex("ComponentInfo\\{([^/]+)/([^}]+)\\}")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    val m = if (comp != null) regex.find(comp) else null
                    if (m != null && drawable != null) {
                        val p = m.groupValues[1]
                        val c = m.groupValues[2].let { if (it.startsWith(".")) p + it else it }
                        map["$p/$c"] = drawable
                    }
                }
                event = parser.next()
            }
            PackState(pack, res, map)
        } catch (_: Exception) {
            PackState(null, null, emptyMap()) // pack missing or unreadable: fall back to system icons
        }
    }
}

private fun Drawable.toBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, size, size)
    draw(Canvas(bmp))
    return bmp
}
