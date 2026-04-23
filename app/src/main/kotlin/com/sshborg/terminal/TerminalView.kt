package com.sshborg.terminal

import android.content.Context
import android.graphics.*
import android.text.InputType
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.*
import kotlin.math.floor

/**
 * Android View that renders a [TerminalEmulator] buffer and accepts keyboard/touch input.
 *
 * Usage:
 *  - Set [emulator] to your [TerminalEmulator] instance.
 *  - Call [onInput] callback to receive characters typed by the user.
 *  - Call [invalidate] after feeding data to the emulator to trigger a redraw.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var emulator: TerminalEmulator? = null
    var onInput: ((ByteArray) -> Unit)? = null
    var onResize: ((cols: Int, rows: Int) -> Unit)? = null

    // --- Fonts and metrics ---
    private val regularTypeface: Typeface by lazy {
        context.assets.open("fonts/JetBrainsMono-Regular.ttf").use { stream ->
            val tmp = java.io.File.createTempFile("jbmono_regular", ".ttf", context.cacheDir)
            tmp.outputStream().use { stream.copyTo(it) }
            Typeface.createFromFile(tmp).also { tmp.delete() }
        }
    }
    private val boldTypeface: Typeface by lazy {
        context.assets.open("fonts/JetBrainsMono-Bold.ttf").use { stream ->
            val tmp = java.io.File.createTempFile("jbmono_bold", ".ttf", context.cacheDir)
            tmp.outputStream().use { stream.copyTo(it) }
            Typeface.createFromFile(tmp).also { tmp.delete() }
        }
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
    }
    private val boldPaint = Paint(textPaint)
    private var cellW = 0f
    private var cellH = 0f
    private var cellBaseline = 0f

    // Scroll offset (in lines) for viewing scrollback
    private var scrollbackOffset = 0

    /** When true, swipe up = see newer content (inverted from natural scroll). */
    var invertScroll: Boolean = false
    private var gestureDetector = GestureDetector(context, GestureListener())
    private var scaleDetector = ScaleGestureDetector(context, ScaleListener())

    // --- ANSI color palette ---
    private val colorPalette = IntArray(256).apply {
        // 0-7: standard
        this[0] = Color.rgb(0, 0, 0);       this[1] = Color.rgb(170, 0, 0)
        this[2] = Color.rgb(0, 170, 0);     this[3] = Color.rgb(170, 85, 0)
        this[4] = Color.rgb(0, 0, 170);     this[5] = Color.rgb(170, 0, 170)
        this[6] = Color.rgb(0, 170, 170);   this[7] = Color.rgb(170, 170, 170)
        // 8-15: bright
        this[8]  = Color.rgb(85, 85, 85);   this[9]  = Color.rgb(255, 85, 85)
        this[10] = Color.rgb(85, 255, 85);  this[11] = Color.rgb(255, 255, 85)
        this[12] = Color.rgb(85, 85, 255);  this[13] = Color.rgb(255, 85, 255)
        this[14] = Color.rgb(85, 255, 255); this[15] = Color.rgb(255, 255, 255)
        // 16-231: 6x6x6 color cube
        for (i in 16..231) {
            val n = i - 16
            val r = n / 36; val g = (n % 36) / 6; val b = n % 6
            this[i] = Color.rgb(if (r > 0) 55 + r * 40 else 0, if (g > 0) 55 + g * 40 else 0, if (b > 0) 55 + b * 40 else 0)
        }
        // 232-255: grayscale
        for (i in 232..255) {
            val v = 8 + (i - 232) * 10
            this[i] = Color.rgb(v, v, v)
        }
    }
    private val defaultFg = Color.rgb(204, 204, 204)
    private val defaultBg = Color.BLACK

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        textPaint.typeface = regularTypeface
        boldPaint.typeface = boldTypeface
        updateMetrics()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { requestFocus() }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) post { reattachIme() }
    }

    fun showKeyboard() {
        post { reattachIme() }
    }

    /**
     * Forza l'IME a riconnettersi a questa view.
     * Necessario in Compose: la ComposeView parent cattura il focus IME
     * e bisogna esplicitamente scalzarla con restartInput().
     */
    private fun reattachIme() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.restartInput(this)
        imm.showSoftInput(this, 0)
    }

    private fun updateMetrics() {
        val fm = textPaint.fontMetrics
        cellH = fm.descent - fm.ascent
        cellBaseline = -fm.ascent
        cellW = textPaint.measureText("M")
    }

    fun setTextSizeSp(sp: Float) {
        val px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        textPaint.textSize = px
        boldPaint.textSize = px
        updateMetrics()
        invalidate()
    }

    // --- Layout ---

    val termColumns: Int get() = if (cellW > 0) floor(width / cellW).toInt().coerceAtLeast(1) else 80
    val termRows: Int get() = if (cellH > 0) floor(height / cellH).toInt().coerceAtLeast(1) else 24

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cols = termColumns; val rows = termRows
        emulator?.let { em -> synchronized(em) { em.resize(cols, rows) } }
        onResize?.invoke(cols, rows)
    }

    // --- Drawing ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val emu = emulator ?: return

        canvas.drawColor(defaultBg)

        val visibleRows = termRows
        val visibleCols = termColumns

        synchronized(emu) {
            val buf = emu.buffer

            // Total lines: scrollback + screen
            val totalScrollback = buf.scrollbackSize
            val viewStart = totalScrollback - scrollbackOffset

            for (r in 0 until visibleRows) {
                val absLine = viewStart + r
                val cells: Array<TerminalBuffer.Cell> = when {
                    absLine < totalScrollback -> buf.getScrollbackLine(absLine) ?: continue
                    else -> {
                        val screenRow = absLine - totalScrollback
                        if (screenRow >= buf.rows) continue
                        (0 until buf.columns).map { c -> buf.getCell(screenRow, c) }.toTypedArray()
                    }
                }

                val top = r * cellH
                val bottom = top + cellH

                for (c in 0 until visibleCols.coerceAtMost(cells.size)) {
                    val cell = cells[c]
                    val style = cell.style
                    val left = c * cellW

                    val (fg, bg) = resolveColors(style)

                    if (bg != defaultBg) {
                        textPaint.color = bg
                        canvas.drawRect(left, top, left + cellW, bottom, textPaint)
                    }

                    if (cell.char != ' ' && !style.invisible) {
                        val paint = if (style.bold) boldPaint else textPaint
                        paint.color = if (style.bold && style.fg in 0..7) colorPalette[style.fg + 8] else fg
                        canvas.drawText(cell.char.toString(), left, top + cellBaseline, paint)

                        if (style.underline) {
                            paint.color = fg
                            canvas.drawLine(left, bottom - 2, left + cellW, bottom - 2, paint)
                        }
                        if (style.strikethrough) {
                            val mid = top + cellH / 2
                            paint.color = fg
                            canvas.drawLine(left, mid, left + cellW, mid, paint)
                        }
                    }
                }

                // Cursor (only on screen portion, not scrollback)
                if (scrollbackOffset == 0 && absLine == totalScrollback + buf.cursorRow && buf.cursorVisible) {
                    val cursorLeft = buf.cursorCol * cellW
                    textPaint.color = defaultFg
                    textPaint.alpha = 180
                    canvas.drawRect(cursorLeft, top, cursorLeft + cellW, bottom, textPaint)
                    textPaint.alpha = 255
                }
            }
        }
    }

    private fun resolveColors(style: TextStyle): Pair<Int, Int> {
        var fg = resolveSingleColor(style.fg, defaultFg)
        var bg = resolveSingleColor(style.bg, defaultBg)
        if (style.inverse) { val t = fg; fg = bg; bg = t }
        return fg to bg
    }

    private fun resolveSingleColor(color: Int, default: Int): Int = when {
        color == TextStyle.COLOR_DEFAULT -> default
        color in 0..255 -> colorPalette[color]
        color and 0xFF000000.toInt() != 0 -> color or 0xFF000000.toInt() // 24-bit
        color >= 0x1000000 -> { // encoded 24-bit RGB
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            Color.rgb(r, g, b)
        }
        else -> default
    }

    // --- Input ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val bytes = keyEventToBytes(keyCode, event) ?: return super.onKeyDown(keyCode, event)
        onInput?.invoke(bytes)
        return true
    }

    private fun keyEventToBytes(keyCode: Int, event: KeyEvent): ByteArray? {
        val ctrl = event.isCtrlPressed
        val alt  = event.isAltPressed
        val shift = event.isShiftPressed
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER      -> byteArrayOf(0x0D)
            KeyEvent.KEYCODE_DEL        -> if (ctrl) byteArrayOf(0x08) else byteArrayOf(0x7F)
            KeyEvent.KEYCODE_TAB        -> if (shift) "\u001b[Z".toByteArray() else byteArrayOf(0x09)
            KeyEvent.KEYCODE_ESCAPE     -> byteArrayOf(0x1B)
            KeyEvent.KEYCODE_DPAD_UP    -> if (emulator?.applicationCursorKeys == true) "\u001bOA".toByteArray() else "\u001b[A".toByteArray()
            KeyEvent.KEYCODE_DPAD_DOWN  -> if (emulator?.applicationCursorKeys == true) "\u001bOB".toByteArray() else "\u001b[B".toByteArray()
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (emulator?.applicationCursorKeys == true) "\u001bOC".toByteArray() else "\u001b[C".toByteArray()
            KeyEvent.KEYCODE_DPAD_LEFT  -> if (emulator?.applicationCursorKeys == true) "\u001bOD".toByteArray() else "\u001b[D".toByteArray()
            KeyEvent.KEYCODE_MOVE_HOME  -> "\u001b[H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END   -> "\u001b[F".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP    -> "\u001b[5~".toByteArray()
            KeyEvent.KEYCODE_PAGE_DOWN  -> "\u001b[6~".toByteArray()
            KeyEvent.KEYCODE_INSERT     -> "\u001b[2~".toByteArray()
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~".toByteArray()
            KeyEvent.KEYCODE_F1  -> "\u001bOP".toByteArray()
            KeyEvent.KEYCODE_F2  -> "\u001bOQ".toByteArray()
            KeyEvent.KEYCODE_F3  -> "\u001bOR".toByteArray()
            KeyEvent.KEYCODE_F4  -> "\u001bOS".toByteArray()
            KeyEvent.KEYCODE_F5  -> "\u001b[15~".toByteArray()
            KeyEvent.KEYCODE_F6  -> "\u001b[17~".toByteArray()
            KeyEvent.KEYCODE_F7  -> "\u001b[18~".toByteArray()
            KeyEvent.KEYCODE_F8  -> "\u001b[19~".toByteArray()
            KeyEvent.KEYCODE_F9  -> "\u001b[20~".toByteArray()
            KeyEvent.KEYCODE_F10 -> "\u001b[21~".toByteArray()
            KeyEvent.KEYCODE_F11 -> "\u001b[23~".toByteArray()
            KeyEvent.KEYCODE_F12 -> "\u001b[24~".toByteArray()
            else -> {
                val ch = event.unicodeChar
                if (ch == 0) return null
                val actual = when {
                    ctrl && ch in 0x40..0x5F -> byteArrayOf((ch - 0x40).toByte())
                    ctrl && ch in 0x61..0x7A -> byteArrayOf((ch - 0x60).toByte())
                    alt -> byteArrayOf(0x1B, ch.toByte())
                    else -> ch.toChar().toString().toByteArray(Charsets.UTF_8)
                }
                actual
            }
        }
    }

    // --- Gesture: scroll and pinch-zoom ---

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val buf = emulator?.buffer ?: return false
            val lines = ((if (invertScroll) dy else -dy) / cellH).toInt()
            scrollbackOffset = (scrollbackOffset + lines).coerceIn(0, buf.scrollbackSize)
            invalidate()
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this@TerminalView, 0)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            val buf = emulator?.buffer ?: return
            val sb = StringBuilder()
            for (i in 0 until buf.scrollbackSize) {
                val line = buf.getScrollbackLine(i) ?: continue
                val row = StringBuilder()
                for (cell in line) row.append(cell.char)
                sb.appendLine(row.trimEnd())
            }
            for (row in 0 until buf.rows) {
                val line = StringBuilder()
                for (col in 0 until buf.columns) {
                    line.append(buf.getCell(row, col).char)
                }
                sb.appendLine(line.trimEnd())
            }
            val text = sb.toString().trimEnd()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
            android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newSize = (textPaint.textSize * detector.scaleFactor).coerceIn(20f, 80f)
            textPaint.textSize = newSize
            boldPaint.textSize = newSize
            updateMetrics()
            val cols = termColumns; val rows = termRows
            // Do NOT call emulator.resize() here — onResize delegates to vm.resize()
            // which does it under the correct synchronized lock.
            onResize?.invoke(cols, rows)
            invalidate()
            return true
        }
    }

    /** Handles soft keyboard input. */
    private inner class TerminalInputConnection(view: View) : BaseInputConnection(view, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            text?.toString()?.toByteArray(Charsets.UTF_8)?.let { onInput?.invoke(it) }
            return true
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength > 0) onInput?.invoke(byteArrayOf(0x7F))
            return true
        }
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val bytes = keyEventToBytes(event.keyCode, event)
                if (bytes != null) { onInput?.invoke(bytes); return true }
            }
            return super.sendKeyEvent(event)
        }
    }
}
