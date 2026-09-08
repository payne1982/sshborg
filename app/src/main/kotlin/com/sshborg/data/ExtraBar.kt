package com.sshborg.data

import androidx.annotation.StringRes
import com.sshborg.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Description of the terminal's extra-key bar (issue #12): one to three rows of
 * keys, a font size, and per row whether the keys stretch to the width or keep
 * their natural size and scroll. Presets live in [ExtraBarPresets]; custom bars
 * are the same type persisted as JSON in [AppPreferences].
 */
data class ExtraBar(
    val id: String,
    /** Display name of a custom bar; presets resolve theirs from [ExtraBarPresets.nameRes]. */
    val name: String,
    val rows: List<ExtraBarRow>,
    val fontScale: BarFontScale = BarFontScale.SMALL,
) {
    val isPreset: Boolean get() = id.startsWith(PRESET_PREFIX)

    companion object {
        const val PRESET_PREFIX = "preset:"
        const val CUSTOM_PREFIX = "custom:"
        const val MAX_ROWS = 3
    }
}

data class ExtraBarRow(
    val keys: List<ExtraKeyDef>,
    /** true: keys share the row width equally; false: natural width, horizontal scroll. */
    val fit: Boolean = false,
)

enum class BarFontScale(val sp: Int) { SMALL(12), MEDIUM(14), LARGE(16) }

/** Fixed terminal keys. Arrows are resolved through the emulator's cursor-key mode. */
enum class SpecialKey(val label: String, val repeat: Boolean = false) {
    ESC("ESC"), TAB("Tab"), ENTER("Enter"), BKSP("⌫", repeat = true), DEL("Del", repeat = true), INS("Ins"),
    HOME("Home"), END("End"), PGUP("PgUp", repeat = true), PGDN("PgDn", repeat = true),
    UP("↑", repeat = true), DOWN("↓", repeat = true), LEFT("←", repeat = true), RIGHT("→", repeat = true),
    F1("F1"), F2("F2"), F3("F3"), F4("F4"), F5("F5"), F6("F6"),
    F7("F7"), F8("F8"), F9("F9"), F10("F10"), F11("F11"), F12("F12");

    val isArrow: Boolean get() = this == UP || this == DOWN || this == LEFT || this == RIGHT

    /** Bytes to send; [cursorKeys] maps a CSI final char (A/B/C/D) honouring application-cursor mode. */
    fun bytes(cursorKeys: (Char) -> ByteArray): ByteArray = when (this) {
        ESC   -> byteArrayOf(0x1B)
        TAB   -> byteArrayOf(0x09)
        ENTER -> byteArrayOf(0x0D)
        BKSP  -> byteArrayOf(0x7F)
        DEL   -> "[3~".toByteArray()
        INS   -> "[2~".toByteArray()
        HOME  -> "[H".toByteArray()
        END   -> "[F".toByteArray()
        PGUP  -> "[5~".toByteArray()
        PGDN  -> "[6~".toByteArray()
        UP    -> cursorKeys('A')
        DOWN  -> cursorKeys('B')
        RIGHT -> cursorKeys('C')
        LEFT  -> cursorKeys('D')
        F1    -> "OP".toByteArray()
        F2    -> "OQ".toByteArray()
        F3    -> "OR".toByteArray()
        F4    -> "OS".toByteArray()
        F5    -> "[15~".toByteArray()
        F6    -> "[17~".toByteArray()
        F7    -> "[18~".toByteArray()
        F8    -> "[19~".toByteArray()
        F9    -> "[20~".toByteArray()
        F10   -> "[21~".toByteArray()
        F11   -> "[23~".toByteArray()
        F12   -> "[24~".toByteArray()
    }
}

/** Sticky one-shot modifiers, applied to the next single byte by the terminal screen. */
enum class ModKey(val label: String) { CTRL("Ctrl"), ALT("Alt") }

/** Bar/app actions rendered as icon keys. */
enum class BarAction { PASTE, PIN, WORD_MODE, SWITCH_BAR, KEYBOARD }

sealed class ExtraKeyDef {
    data class Special(val key: SpecialKey, val label: String? = null) : ExtraKeyDef()
    data class Modifier(val mod: ModKey) : ExtraKeyDef()
    /** Literal text sent as-is; `\n \t \e \\` escapes are expanded by [unescaped]. */
    data class Text(val text: String, val label: String? = null) : ExtraKeyDef() {
        val unescaped: String get() = unescapeKeyText(text)
    }
    data class Action(val action: BarAction) : ExtraKeyDef()

    /** Label drawn on the key (actions draw icons instead). */
    val displayLabel: String
        get() = when (this) {
            is Special  -> label?.takeIf { it.isNotBlank() } ?: key.label
            is Modifier -> mod.label
            is Text     -> label?.takeIf { it.isNotBlank() } ?: text
            is Action   -> ""
        }

    val repeatOnHold: Boolean get() = this is Special && key.repeat
}

fun unescapeKeyText(s: String): String {
    if (!s.contains('\\')) return s
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (s[i + 1]) {
                'n'  -> { out.append('\n'); i += 2; continue }
                'r'  -> { out.append('\r'); i += 2; continue }
                't'  -> { out.append('\t'); i += 2; continue }
                'e'  -> { out.append(''); i += 2; continue }
                '\\' -> { out.append('\\'); i += 2; continue }
            }
        }
        out.append(c); i++
    }
    return out.toString()
}

/** Built-in layouts. Never persisted, so they may change between versions. */
object ExtraBarPresets {
    const val STANDARD    = "${ExtraBar.PRESET_PREFIX}standard"
    const val NATURAL     = "${ExtraBar.PRESET_PREFIX}natural"
    const val NATURAL_2   = "${ExtraBar.PRESET_PREFIX}natural_2"
    const val NATURAL_3   = "${ExtraBar.PRESET_PREFIX}natural_3"
    const val MINIMAL     = "${ExtraBar.PRESET_PREFIX}minimal"

    private fun sp(k: SpecialKey) = ExtraKeyDef.Special(k)
    private fun mod(m: ModKey) = ExtraKeyDef.Modifier(m)
    private fun txt(t: String) = ExtraKeyDef.Text(t)
    private fun act(a: BarAction) = ExtraKeyDef.Action(a)
    private val fKeys = listOf(
        SpecialKey.F1, SpecialKey.F2, SpecialKey.F3, SpecialKey.F4, SpecialKey.F5, SpecialKey.F6,
        SpecialKey.F7, SpecialKey.F8, SpecialKey.F9, SpecialKey.F10, SpecialKey.F11, SpecialKey.F12,
    ).map { sp(it) }

    /** The bar as it has always been: one scrolling row. */
    private val standard = ExtraBar(
        id = STANDARD, name = "", fontScale = BarFontScale.SMALL,
        rows = listOf(ExtraBarRow(
            listOf(mod(ModKey.CTRL), mod(ModKey.ALT), act(BarAction.WORD_MODE),
                sp(SpecialKey.ESC), sp(SpecialKey.TAB),
                sp(SpecialKey.UP), sp(SpecialKey.DOWN), sp(SpecialKey.LEFT), sp(SpecialKey.RIGHT),
                sp(SpecialKey.HOME), sp(SpecialKey.END), sp(SpecialKey.PGUP), sp(SpecialKey.PGDN),
                sp(SpecialKey.DEL), act(BarAction.PASTE), act(BarAction.PIN),
            ) + fKeys + act(BarAction.SWITCH_BAR),
        )),
    )

    /**
     * One scrolling row ordered by frequency of use: modifiers and ESC/Tab first, the
     * shell symbols hidden behind a layer on phone keyboards, arrows in physical-keyboard
     * order (← ↑ ↓ →), page navigation, then the F keys in the tail.
     */
    private val natural = ExtraBar(
        id = NATURAL, name = "", fontScale = BarFontScale.MEDIUM,
        rows = listOf(ExtraBarRow(
            listOf(sp(SpecialKey.ESC), sp(SpecialKey.TAB), mod(ModKey.CTRL), mod(ModKey.ALT), act(BarAction.WORD_MODE),
                txt("/"), txt("-"), txt("|"), txt("~"),
                sp(SpecialKey.LEFT), sp(SpecialKey.UP), sp(SpecialKey.DOWN), sp(SpecialKey.RIGHT),
                sp(SpecialKey.HOME), sp(SpecialKey.END), sp(SpecialKey.PGUP), sp(SpecialKey.PGDN),
                sp(SpecialKey.DEL), act(BarAction.PASTE), act(BarAction.PIN),
            ) + fKeys + act(BarAction.SWITCH_BAR),
        )),
    )

    /** The widespread two-row arrangement (#12): arrows in a cross, page keys at the ends. */
    private val naturalRows = listOf(
        ExtraBarRow(listOf(
            sp(SpecialKey.ESC), txt("/"), txt("-"), sp(SpecialKey.HOME), sp(SpecialKey.UP),
            sp(SpecialKey.END), sp(SpecialKey.PGUP), act(BarAction.PASTE), act(BarAction.PIN),
        ), fit = true),
        ExtraBarRow(listOf(
            sp(SpecialKey.TAB), mod(ModKey.CTRL), mod(ModKey.ALT), sp(SpecialKey.LEFT), sp(SpecialKey.DOWN),
            sp(SpecialKey.RIGHT), sp(SpecialKey.PGDN), act(BarAction.WORD_MODE), act(BarAction.SWITCH_BAR),
        ), fit = true),
    )
    private val natural2 = ExtraBar(NATURAL_2, "", naturalRows, BarFontScale.MEDIUM)
    private val natural3 = ExtraBar(NATURAL_3, "", naturalRows + ExtraBarRow(fKeys, fit = false), BarFontScale.MEDIUM)

    private val minimal = ExtraBar(
        MINIMAL, "", listOf(ExtraBarRow(listOf(
            sp(SpecialKey.ESC), sp(SpecialKey.TAB), mod(ModKey.CTRL),
            sp(SpecialKey.UP), sp(SpecialKey.DOWN), sp(SpecialKey.LEFT), sp(SpecialKey.RIGHT),
            act(BarAction.SWITCH_BAR),
        ), fit = true)),
        BarFontScale.MEDIUM,
    )

    val all: List<ExtraBar> = listOf(standard, natural, natural2, natural3, minimal)

    fun byId(id: String): ExtraBar? = all.find { it.id == id }

    @StringRes
    fun nameRes(id: String): Int = when (id) {
        NATURAL     -> R.string.extra_bar_preset_natural
        NATURAL_2   -> R.string.extra_bar_preset_natural_2
        NATURAL_3   -> R.string.extra_bar_preset_natural_3
        MINIMAL     -> R.string.extra_bar_preset_minimal
        else        -> R.string.extra_bar_preset_standard
    }
}

/**
 * JSON (de)serialiser for custom bars. Unknown key kinds/values are dropped on
 * load rather than failing, so a newer backup degrades gracefully.
 */
object ExtraBarJson {
    private const val FORMAT = 1

    fun encode(bar: ExtraBar): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("id", bar.id)
        put("name", bar.name)
        put("font", bar.fontScale.name)
        put("rows", JSONArray().apply {
            bar.rows.forEach { row ->
                put(JSONObject().apply {
                    put("fit", row.fit)
                    put("keys", JSONArray().apply { row.keys.forEach { put(encodeKey(it)) } })
                })
            }
        })
    }

    fun encodeAll(bars: List<ExtraBar>): String =
        JSONArray().apply { bars.forEach { put(encode(it)) } }.toString()

    fun decode(obj: JSONObject): ExtraBar? {
        val id = obj.optString("id").takeIf { it.startsWith(ExtraBar.CUSTOM_PREFIX) } ?: return null
        val font = runCatching { BarFontScale.valueOf(obj.optString("font")) }.getOrDefault(BarFontScale.SMALL)
        val rowsArr = obj.optJSONArray("rows") ?: return null
        val rows = (0 until rowsArr.length()).mapNotNull { i ->
            val r = rowsArr.optJSONObject(i) ?: return@mapNotNull null
            val keysArr = r.optJSONArray("keys") ?: JSONArray()
            val keys = (0 until keysArr.length()).mapNotNull { j -> keysArr.optJSONObject(j)?.let(::decodeKey) }
            ExtraBarRow(keys, r.optBoolean("fit", false))
        }.take(ExtraBar.MAX_ROWS)
        if (rows.isEmpty()) return null
        return ExtraBar(id, obj.optString("name"), rows, font)
    }

    fun decodeAll(json: String?): List<ExtraBar> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::decode) }
    }

    private fun encodeKey(k: ExtraKeyDef): JSONObject = JSONObject().apply {
        when (k) {
            is ExtraKeyDef.Special  -> { put("k", "special"); put("v", k.key.name); k.label?.let { put("l", it) } }
            is ExtraKeyDef.Modifier -> { put("k", "mod");     put("v", k.mod.name) }
            is ExtraKeyDef.Text     -> { put("k", "text");    put("v", k.text);     k.label?.let { put("l", it) } }
            is ExtraKeyDef.Action   -> { put("k", "action");  put("v", k.action.name) }
        }
    }

    private fun decodeKey(o: JSONObject): ExtraKeyDef? {
        val v = o.optString("v")
        val label = if (o.has("l")) o.optString("l") else null
        return when (o.optString("k")) {
            "special" -> runCatching { SpecialKey.valueOf(v) }.getOrNull()?.let { ExtraKeyDef.Special(it, label) }
            "mod"     -> runCatching { ModKey.valueOf(v) }.getOrNull()?.let { ExtraKeyDef.Modifier(it) }
            "text"    -> ExtraKeyDef.Text(v, label)
            "action"  -> runCatching { BarAction.valueOf(v) }.getOrNull()?.let { ExtraKeyDef.Action(it) }
            else      -> null
        }
    }
}
