package com.sshborg.terminal

import android.content.Context
import android.graphics.*
import android.text.InputType
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.*
import kotlin.math.floor
import kotlin.math.hypot

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

    // --- Selection state ---
    // Anchors are (absLine, col) where absLine counts from the top of scrollback + screen.
    // selStart is always <= selEnd (normalized on every update).
    private var selStart: Pair<Int, Int>? = null
    private var selEnd:   Pair<Int, Int>? = null
    private var draggingHandle = 0  // 0=none, 1=start, 2=end
    private var cachedViewStart = 0 // set each onDraw; safe to read on main thread in touch handlers
    private var dragLastX = 0f

    // Auto-scroll while dragging a handle near the top/bottom edge.
    private val autoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null
    private var autoScrollDir = 0  // +1 = toward older content (up), -1 = toward newer (down)

    val inSelectionMode: Boolean get() = selStart != null
    var onSelectionModeChanged: ((Boolean) -> Unit)? = null

    private val selectionPaint = Paint().apply { color = Color.argb(80, 100, 149, 237) }
    private val handlePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 149, 237) }
    private val handleRadius: Float by lazy { 10f * resources.displayMetrics.density }

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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAutoScroll()
    }

    fun showKeyboard() {
        post { reattachIme() }
    }

    /**
     * Force IME to reconnect to this view.
     * Needed in Compose: the ComposeView parent captures IME focus and we must
     * explicitly displace it with restartInput().
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

            val totalScrollback = buf.scrollbackSize
            val viewStart = totalScrollback - scrollbackOffset
            cachedViewStart = viewStart

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

        if (inSelectionMode) drawSelection(canvas)
    }

    // --- Selection drawing ---

    private fun drawSelection(canvas: Canvas) {
        val start = selStart ?: return
        val end   = selEnd   ?: return
        val (s, e) = if (compareAnchors(start, end) <= 0) start to end else end to start
        val vStart = cachedViewStart

        // Highlight selected cells with a semi-transparent overlay.
        // First line: from s.col to end of row.
        // Middle lines: entire row.
        // Last line: from start of row to e.col.
        for (r in 0 until termRows) {
            val absLine = vStart + r
            if (absLine < s.first || absLine > e.first) continue
            val top    = r * cellH
            val bottom = top + cellH
            val left   = if (absLine == s.first) s.second * cellW else 0f
            val right  = if (absLine == e.first) (e.second + 1) * cellW else width.toFloat()
            canvas.drawRect(left, top, right, bottom, selectionPaint)
        }

        // Start handle: stem covers the first selected row, circle above.
        val startScreenRow = s.first - vStart
        if (startScreenRow in 0 until termRows) {
            drawHandle(canvas, s.second * cellW, startScreenRow * cellH, isStart = true)
        }

        // End handle: stem covers the last selected row, circle below.
        val endScreenRow = e.first - vStart
        if (endScreenRow in 0 until termRows) {
            drawHandle(canvas, (e.second + 1) * cellW, endScreenRow * cellH, isStart = false)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, rowTop: Float, isStart: Boolean) {
        val r = handleRadius
        canvas.drawRect(x - 2f, rowTop, x + 2f, rowTop + cellH, handlePaint)
        if (isStart) canvas.drawCircle(x, rowTop - r, r, handlePaint)
        else         canvas.drawCircle(x, rowTop + cellH + r, r, handlePaint)
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

    // --- Touch and selection interaction ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (inSelectionMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    draggingHandle = hitTestHandle(event.x, event.y)
                    if (draggingHandle != 0) return true
                    // Not on a handle: fall through to gesture detector (allows scroll/tap-to-exit)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (draggingHandle != 0) {
                        dragLastX = event.x
                        val triggerZone = cellH * 2f
                        when {
                            event.y < triggerZone          -> scheduleAutoScroll(+1)
                            event.y > height - triggerZone -> scheduleAutoScroll(-1)
                            else -> { cancelAutoScroll(); updateDraggedHandle(event.x, event.y) }
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (draggingHandle != 0) {
                        cancelAutoScroll()
                        draggingHandle = 0
                        return true
                    }
                }
            }
        }
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    private fun pixelToAnchor(x: Float, y: Float): Pair<Int, Int> {
        val row = (y / cellH).toInt().coerceIn(0, termRows - 1)
        val col = (x / cellW).toInt().coerceIn(0, termColumns - 1)
        return (cachedViewStart + row) to col
    }

    private fun hitTestHandle(x: Float, y: Float): Int {
        val hitRadius = handleRadius * 2.5f
        val start = selStart ?: return 0
        val end   = selEnd   ?: return 0
        val (s, e) = if (compareAnchors(start, end) <= 0) start to end else end to start

        val startRow = s.first - cachedViewStart
        if (startRow in 0 until termRows) {
            val hx = s.second * cellW
            val hy = startRow * cellH - handleRadius          // circle centre above the row
            if (hypot((x - hx).toDouble(), (y - hy).toDouble()) < hitRadius) return 1
        }

        val endRow = e.first - cachedViewStart
        if (endRow in 0 until termRows) {
            val hx = (e.second + 1) * cellW
            val hy = endRow * cellH + cellH + handleRadius    // circle centre below the row
            if (hypot((x - hx).toDouble(), (y - hy).toDouble()) < hitRadius) return 2
        }

        return 0
    }

    private fun updateDraggedHandle(x: Float, y: Float) {
        applyDraggedAnchor(pixelToAnchor(x, y))
        invalidate()
    }

    private fun applyDraggedAnchor(anchor: Pair<Int, Int>) {
        if (draggingHandle == 1) {
            val end = selEnd!!
            if (compareAnchors(anchor, end) > 0) {
                selStart = end; selEnd = anchor; draggingHandle = 2
            } else {
                selStart = anchor
            }
        } else if (draggingHandle == 2) {
            val start = selStart!!
            if (compareAnchors(anchor, start) < 0) {
                selEnd = start; selStart = anchor; draggingHandle = 1
            } else {
                selEnd = anchor
            }
        }
    }

    private fun scheduleAutoScroll(dir: Int) {
        if (autoScrollDir == dir) return
        cancelAutoScroll()
        autoScrollDir = dir
        val r = object : Runnable {
            override fun run() {
                if (autoScrollDir == 0 || draggingHandle == 0) return
                val emu = emulator ?: return
                val maxScrollback: Int
                synchronized(emu) { maxScrollback = emu.buffer.scrollbackSize }
                scrollbackOffset = (scrollbackOffset + autoScrollDir).coerceIn(0, maxScrollback)
                val viewStart = maxScrollback - scrollbackOffset
                val edgeRow = if (autoScrollDir > 0) 0 else termRows - 1
                val col = (dragLastX / cellW).toInt().coerceIn(0, termColumns - 1)
                applyDraggedAnchor((viewStart + edgeRow) to col)
                invalidate()
                autoScrollHandler.postDelayed(this, 80)
            }
        }
        autoScrollRunnable = r
        autoScrollHandler.postDelayed(r, 80)
    }

    private fun cancelAutoScroll() {
        autoScrollDir = 0
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        autoScrollRunnable = null
    }

    private fun compareAnchors(a: Pair<Int, Int>, b: Pair<Int, Int>): Int =
        if (a.first != b.first) a.first - b.first else a.second - b.second

    fun exitSelectionMode() {
        selStart = null
        selEnd   = null
        draggingHandle = 0
        onSelectionModeChanged?.invoke(false)
        invalidate()
    }

    fun getSelectedText(): String {
        val start = selStart ?: return ""
        val end   = selEnd   ?: return ""
        val (s, e) = if (compareAnchors(start, end) <= 0) start to end else end to start
        val emu = emulator ?: return ""
        val sb = StringBuilder()
        synchronized(emu) {
            val buf   = emu.buffer
            val total = buf.scrollbackSize
            for (absLine in s.first..e.first) {
                val cells = getAbsLineCells(absLine, buf, total) ?: continue
                val from  = if (absLine == s.first) s.second else 0
                val to    = (if (absLine == e.first) e.second else cells.lastIndex).coerceAtMost(cells.lastIndex)
                val row   = StringBuilder()
                for (col in from..to) row.append(cells[col].char)
                if (absLine < e.first) sb.appendLine(row.trimEnd()) else sb.append(row.trimEnd())
            }
        }
        return sb.toString().trimEnd()
    }

    fun getAllText(): String {
        val emu = emulator ?: return ""
        val sb = StringBuilder()
        synchronized(emu) {
            val buf = emu.buffer
            for (i in 0 until buf.scrollbackSize) {
                val line = buf.getScrollbackLine(i) ?: continue
                val row = StringBuilder()
                for (cell in line) row.append(cell.char)
                sb.appendLine(row.trimEnd())
            }
            for (row in 0 until buf.rows) {
                val line = StringBuilder()
                for (col in 0 until buf.columns) line.append(buf.getCell(row, col).char)
                sb.appendLine(line.trimEnd())
            }
        }
        return sb.toString().trimEnd()
    }

    private fun getAbsLineCells(absLine: Int, buf: TerminalBuffer, totalScrollback: Int): Array<TerminalBuffer.Cell>? =
        if (absLine < totalScrollback) {
            buf.getScrollbackLine(absLine)
        } else {
            val sr = absLine - totalScrollback
            if (sr >= buf.rows) null else (0 until buf.columns).map { buf.getCell(sr, it) }.toTypedArray()
        }

    // --- Input ---

    override fun onCheckIsTextEditor() = true

    var wordMode: Boolean = false
        set(value) {
            if (field != value) { field = value; post { reattachIme() } }
        }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = if (wordMode)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        else
            InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val bytes = keyEventToBytes(keyCode, event) ?: return super.onKeyDown(keyCode, event)
        onInput?.invoke(bytes)
        return true
    }

    private fun keyEventToBytes(keyCode: Int, event: KeyEvent): ByteArray? {
        val ctrl  = event.isCtrlPressed
        val alt   = event.isAltPressed
        val shift = event.isShiftPressed
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER       -> byteArrayOf(0x0D)
            KeyEvent.KEYCODE_DEL         -> if (ctrl) byteArrayOf(0x08) else byteArrayOf(0x7F)
            KeyEvent.KEYCODE_TAB         -> if (shift) "[Z".toByteArray() else byteArrayOf(0x09)
            KeyEvent.KEYCODE_ESCAPE      -> byteArrayOf(0x1B)
            KeyEvent.KEYCODE_DPAD_UP     -> if (emulator?.applicationCursorKeys == true) "OA".toByteArray() else "[A".toByteArray()
            KeyEvent.KEYCODE_DPAD_DOWN   -> if (emulator?.applicationCursorKeys == true) "OB".toByteArray() else "[B".toByteArray()
            KeyEvent.KEYCODE_DPAD_RIGHT  -> if (emulator?.applicationCursorKeys == true) "OC".toByteArray() else "[C".toByteArray()
            KeyEvent.KEYCODE_DPAD_LEFT   -> if (emulator?.applicationCursorKeys == true) "OD".toByteArray() else "[D".toByteArray()
            KeyEvent.KEYCODE_MOVE_HOME   -> "[H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END    -> "[F".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP     -> "[5~".toByteArray()
            KeyEvent.KEYCODE_PAGE_DOWN   -> "[6~".toByteArray()
            KeyEvent.KEYCODE_INSERT      -> "[2~".toByteArray()
            KeyEvent.KEYCODE_FORWARD_DEL -> "[3~".toByteArray()
            KeyEvent.KEYCODE_F1  -> "OP".toByteArray()
            KeyEvent.KEYCODE_F2  -> "OQ".toByteArray()
            KeyEvent.KEYCODE_F3  -> "OR".toByteArray()
            KeyEvent.KEYCODE_F4  -> "OS".toByteArray()
            KeyEvent.KEYCODE_F5  -> "[15~".toByteArray()
            KeyEvent.KEYCODE_F6  -> "[17~".toByteArray()
            KeyEvent.KEYCODE_F7  -> "[18~".toByteArray()
            KeyEvent.KEYCODE_F8  -> "[19~".toByteArray()
            KeyEvent.KEYCODE_F9  -> "[20~".toByteArray()
            KeyEvent.KEYCODE_F10 -> "[21~".toByteArray()
            KeyEvent.KEYCODE_F11 -> "[23~".toByteArray()
            KeyEvent.KEYCODE_F12 -> "[24~".toByteArray()
            else -> {
                val ch = event.unicodeChar
                if (ch == 0) return null
                when {
                    ctrl && ch in 0x40..0x5F -> byteArrayOf((ch - 0x40).toByte())
                    ctrl && ch in 0x61..0x7A -> byteArrayOf((ch - 0x60).toByte())
                    alt  -> byteArrayOf(0x1B, ch.toByte())
                    else -> ch.toChar().toString().toByteArray(Charsets.UTF_8)
                }
            }
        }
    }

    // --- Gesture: scroll, tap, long-press, pinch-zoom ---

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val buf = emulator?.buffer ?: return false
            val lines = ((if (invertScroll) dy else -dy) / cellH).toInt()
            scrollbackOffset = (scrollbackOffset + lines).coerceIn(0, buf.scrollbackSize)
            invalidate()
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (inSelectionMode) {
                exitSelectionMode()
                return true
            }
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this@TerminalView, 0)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (inSelectionMode) return
            val anchor = pixelToAnchor(e.x, e.y)
            selStart = anchor
            selEnd   = anchor
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onSelectionModeChanged?.invoke(true)
            invalidate()
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
        // Silently reject rich content (images, stickers) — returning false would
        // trigger the system "App doesn't support images" toast on Android 12+.
        override fun commitContent(
            inputContentInfo: android.view.inputmethod.InputContentInfo,
            flags: Int,
            opts: android.os.Bundle?,
        ) = true
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength) { onInput?.invoke(byteArrayOf(0x7F)) }
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
