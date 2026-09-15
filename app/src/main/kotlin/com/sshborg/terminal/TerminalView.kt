package com.sshborg.terminal

import android.content.Context
import android.graphics.*
import android.text.InputType
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.*
import com.sshborg.data.AppPreferences
import kotlin.math.abs
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
        context.assets.open("fonts/JetBrainsMonoNerdFontMono-Regular.ttf").use { stream ->
            val tmp = java.io.File.createTempFile("jbmono_regular", ".ttf", context.cacheDir)
            tmp.outputStream().use { stream.copyTo(it) }
            Typeface.createFromFile(tmp).also { tmp.delete() }
        }
    }
    private val boldTypeface: Typeface by lazy {
        context.assets.open("fonts/JetBrainsMonoNerdFontMono-Bold.ttf").use { stream ->
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

    // Reused char buffer for drawText, so each drawn cell no longer allocates a String
    // every frame. Sized 2 to hold a supplementary codepoint's surrogate pair (emoji).
    private val charBuf = CharArray(2)

    // Scroll offset (in lines) for viewing scrollback
    private var scrollbackOffset = 0

    // Sub-cell scroll remainder. onScroll's dy is a per-event delta; truncating
    // (dy / cellH) to whole lines every event discarded the fraction, so slow drags
    // never accumulated enough to move and felt laggy/dropped. Carry it across events.
    private var scrollRemainderY = 0f

    // Wheel-report accumulators: one for travel in lines (finger drag or momentum), one for a
    // real wheel's notches (fractional on precision mice). Both convert into whole notches.
    private var wheelTravel = 0f
    private var wheelNotches = 0f

    /** When true, swipe up = see newer content (inverted from natural scroll). */
    var invertScroll: Boolean = false

    /**
     * What a double-tap sends to the shell: one of AppPreferences.DOUBLE_TAP_*.
     * Default NONE, so the gesture does nothing unless the user opts in (issue #4).
     */
    var doubleTapAction: Int = AppPreferences.DOUBLE_TAP_NONE

    /**
     * Default font size from settings. Applied as long as the user hasn't pinch-zoomed:
     * once they do, the pinched size takes over for the rest of the session.
     */
    var fontSizeSp: Float = 0f
        set(value) {
            if (value == field) return
            field = value
            if (!userScaled && value > 0f) setTextSizeSp(value)
        }
    private var userScaled = false
    private var gestureDetector = GestureDetector(context, GestureListener())
    private var scaleDetector = ScaleGestureDetector(context, ScaleListener())

    // Momentum scrolling (fling). The scroller runs in a pixel space of
    // scrollbackOffset * cellH, so its position maps straight back to whole lines.
    private val scroller = android.widget.OverScroller(context)
    private var flingRunnable: Runnable? = null

    // --- Selection state ---
    // Anchors are (absLine, col) where absLine counts from the top of scrollback + screen.
    // selStart is always <= selEnd (normalized on every update).
    private var selStart: Pair<Int, Int>? = null
    private var selEnd:   Pair<Int, Int>? = null
    private var draggingHandle = 0  // 0=none, 1=start, 2=end
    private var cachedViewStart = 0 // set each onDraw; safe to read on main thread in touch handlers
    private var dragLastX = 0f
    private var dragLastY = 0f
    // Finger-to-anchor offset captured at grab time, so the handle can be dragged by its
    // round knob (above/below the row) without the selection jumping to the finger's row.
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

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
    private val darkPalette = IntArray(256).apply {
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

    // Light-scheme palette: same hues, but the entries that are unreadable on a
    // white background (light grey and most brights) are darkened. Cube and
    // grayscale (16-255) are shared with the dark palette, as explicit colors.
    private val lightPalette = darkPalette.copyOf().apply {
        this[7]  = Color.rgb(115, 115, 115)
        this[9]  = Color.rgb(220, 50, 50)
        this[10] = Color.rgb(0, 135, 0)
        this[11] = Color.rgb(140, 120, 0)
        this[13] = Color.rgb(200, 50, 200)
        this[14] = Color.rgb(0, 145, 160)
        this[15] = Color.rgb(50, 50, 50)
    }

    private var colorPalette = darkPalette
    private var defaultFg = DARK_FG
    private var defaultBg = DARK_BG

    /** Black-on-white color scheme; cells with explicit colors are unaffected. */
    var lightScheme: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            colorPalette = if (value) lightPalette else darkPalette
            defaultFg = if (value) LIGHT_FG else DARK_FG
            defaultBg = if (value) LIGHT_BG else DARK_BG
            invalidate()
        }

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
        cancelFling()
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
        if (px == textPaint.textSize) return
        textPaint.textSize = px
        boldPaint.textSize = px
        updateMetrics()
        // Cell size changed, so the grid dimensions did too (no-op before first layout)
        if (width > 0 && height > 0) onResize?.invoke(termColumns, termRows)
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
        // Claim the whole terminal so the system back-gesture (Android 10+ gesture nav)
        // doesn't steal drags that start near the left/right edge — otherwise those
        // scroll touches get eaten by the OS before reaching us.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
        }
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
            // A full-screen app may have opened while the view was scrolled up into the
            // history: pin it back to the live screen, otherwise the app would repaint in
            // the middle of unrelated shell output.
            val cap = if (emu.altScreenActive) 0 else totalScrollback
            if (scrollbackOffset > cap) { cancelFling(); scrollbackOffset = cap }
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
                    // Trailing half of a wide char: covered by the lead's 2-cell span, skip it.
                    if (cell.trailing) continue
                    val style = cell.style
                    val left = c * cellW
                    // A wide char (emoji/CJK) spans two cells so it isn't clipped mid-glyph.
                    val span = if (cell.wide) cellW * 2 else cellW

                    val (fg, bg) = resolveColors(style)

                    if (bg != defaultBg) {
                        textPaint.color = bg
                        canvas.drawRect(left, top, left + span, bottom, textPaint)
                    }

                    if (cell.code != ' '.code && !style.invisible) {
                        val paint = if (style.bold) boldPaint else textPaint
                        paint.color = if (style.bold && style.fg in 0..7) colorPalette[style.fg + 8] else fg
                        val n = Character.toChars(cell.code, charBuf, 0)
                        canvas.drawText(charBuf, 0, n, left, top + cellBaseline, paint)

                        if (style.underline) {
                            paint.color = fg
                            canvas.drawLine(left, bottom - 2, left + span, bottom - 2, paint)
                        }
                        if (style.strikethrough) {
                            val mid = top + cellH / 2
                            paint.color = fg
                            canvas.drawLine(left, mid, left + span, mid, paint)
                        }
                    }
                }

                // Cursor (only on screen portion, not scrollback)
                if (scrollbackOffset == 0 && absLine == totalScrollback + buf.cursorRow && buf.cursorVisible) {
                    val cursorLeft = buf.cursorCol * cellW
                    val cursorSpan = if (buf.getCell(buf.cursorRow, buf.cursorCol).wide) cellW * 2 else cellW
                    textPaint.color = defaultFg
                    textPaint.alpha = 180
                    canvas.drawRect(cursorLeft, top, cursorLeft + cursorSpan, bottom, textPaint)
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
                    if (draggingHandle != 0) {
                        captureDragOffset(event.x, event.y)
                        return true
                    }
                    // Not on a handle: fall through to gesture detector (allows scroll/tap-to-exit)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (draggingHandle != 0) {
                        dragLastX = event.x + dragOffsetX
                        dragLastY = event.y + dragOffsetY
                        // Always track the finger's real row/col — never snap the handle to
                        // the edge row. That snap was the "magnetic jump" to the last/first
                        // line when the finger neared the top/bottom edge.
                        updateDraggedHandle(dragLastX, dragLastY)
                        // Auto-scroll only inside the edge band AND only when there is
                        // actually more scrollback to reveal in that direction; otherwise the
                        // handle simply follows the finger to the last visible row.
                        val triggerZone = cellH * 1.5f
                        val dir = when {
                            event.y < triggerZone          -> +1
                            event.y > height - triggerZone -> -1
                            else -> 0
                        }
                        if (dir != 0 && canAutoScroll(dir)) scheduleAutoScroll(dir) else cancelAutoScroll()
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

    /**
     * Records the offset between the finger and the centre of the cell the grabbed
     * handle is anchored to. Applying it to every move keeps the anchor exactly where
     * it was at grab time, no matter which part of the handle the finger landed on.
     */
    private fun captureDragOffset(x: Float, y: Float) {
        val start = selStart ?: return
        val end   = selEnd   ?: return
        val (s, e) = if (compareAnchors(start, end) <= 0) start to end else end to start
        val anchor = if (draggingHandle == 1) s else e
        dragOffsetX = (anchor.second + 0.5f) * cellW - x
        dragOffsetY = (anchor.first - cachedViewStart + 0.5f) * cellH - y
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

    /**
     * Highest scrollback offset the view may show. The alternate screen (vim, less, tmux…)
     * has no history of its own, so there the view stays pinned to the live screen: scrolling
     * it would drag up the shell history sitting underneath the full-screen app, which is
     * neither what the app is drawing nor anything the user can act on.
     */
    private val maxScrollOffset: Int
        get() {
            val emu = emulator ?: return 0
            synchronized(emu) { return if (emu.altScreenActive) 0 else emu.buffer.scrollbackSize }
        }

    /**
     * True when wheel movement should be reported to the remote app instead of scrolling the
     * view. Deliberately limited to the alternate screen: on the main screen there is real
     * history above the cursor and scrolling it locally is what the user is after, while on
     * the alternate screen the view is pinned (see [maxScrollOffset]), so the notches have
     * nothing to do here and are better spent on the app, which scrolls itself — in tmux,
     * that means its own copy-mode history rather than the shell output underneath it.
     */
    private fun reportsWheel(emu: TerminalEmulator): Boolean {
        // While the user is selecting, gestures belong to the selection, not to the app.
        if (inSelectionMode) return false
        synchronized(emu) { return emu.altScreenActive && emu.mouseReporting }
    }

    /** Turns whatever whole notches have accumulated in [wheelTravel] into a report. */
    private fun emitWheelTravel(emu: TerminalEmulator, x: Float, y: Float) {
        val notches = (wheelTravel / WHEEL_NOTCH_LINES).toInt()
        if (notches == 0) return
        wheelTravel -= notches * WHEEL_NOTCH_LINES
        sendWheel(emu, notches, x, y)
    }

    /**
     * Reports [notches] wheel steps (positive = up/back) at the given pixel position.
     * Coordinates matter: they tell the app which pane the pointer — or the finger — is over.
     */
    private fun sendWheel(emu: TerminalEmulator, notches: Int, x: Float, y: Float) {
        if (notches == 0 || cellW <= 0f || cellH <= 0f) return
        val col = (x / cellW).toInt().coerceIn(0, (termColumns - 1).coerceAtLeast(0))
        val row = (y / cellH).toInt().coerceIn(0, (termRows - 1).coerceAtLeast(0))
        val out = java.io.ByteArrayOutputStream()
        repeat(abs(notches)) {
            val report = synchronized(emu) { emu.mouseWheelReport(notches > 0, col, row) } ?: return@repeat
            out.write(report)
        }
        if (out.size() > 0) onInput?.invoke(out.toByteArray())
    }

    /** True if there is scrollback left to reveal in [dir] (+1 = older/up, -1 = newer/down). */
    private fun canAutoScroll(dir: Int): Boolean {
        if (emulator == null) return false
        return if (dir > 0) scrollbackOffset < maxScrollOffset else scrollbackOffset > 0
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
                val newOffset = (scrollbackOffset + autoScrollDir).coerceIn(0, maxScrollOffset)
                if (newOffset == scrollbackOffset) { cancelAutoScroll(); return }  // nothing left to reveal
                scrollbackOffset = newOffset
                val viewStart = maxScrollback - scrollbackOffset
                // Extend from the finger's actual row, not a forced edge row.
                val row = (dragLastY / cellH).toInt().coerceIn(0, termRows - 1)
                val col = (dragLastX / cellW).toInt().coerceIn(0, termColumns - 1)
                applyDraggedAnchor((viewStart + row) to col)
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

    /**
     * Momentum scroll after a flick. [velocity] is in the same (accumulator) pixel
     * space as onScroll, i.e. already sign-adjusted for invertScroll by the caller.
     */
    private fun startFling(velocity: Float) {
        if (emulator == null) return
        if (cellH <= 0f) return
        cancelFling()
        val maxPixels = (maxScrollOffset * cellH).toInt()
        if (maxPixels <= 0) return
        val startY = (scrollbackOffset * cellH).toInt().coerceIn(0, maxPixels)
        scroller.fling(0, startY, 0, velocity.toInt(), 0, 0, 0, maxPixels)
        val r = object : Runnable {
            override fun run() {
                if (!scroller.computeScrollOffset()) { flingRunnable = null; return }
                scrollbackOffset = (scroller.currY / cellH).toInt().coerceIn(0, maxScrollOffset)
                invalidate()
                if (scroller.isFinished) flingRunnable = null else postOnAnimation(this)
            }
        }
        flingRunnable = r
        postOnAnimation(r)
    }

    /**
     * Momentum for a reported wheel. The view itself can't move here (it is pinned to the
     * alternate screen), so instead of scrolling pixels the scroller is run over a virtual
     * span and each frame's travel is turned into further notches: the remote app keeps
     * scrolling and decelerates on its own. [velocity] uses onScroll's sign convention.
     */
    private fun startWheelFling(emu: TerminalEmulator, velocity: Float, x: Float, y: Float) {
        if (cellH <= 0f) return
        cancelFling()
        // Start halfway so the fling can run either way, and let the span cap the burst.
        val span = (WHEEL_FLING_MAX_LINES * cellH).toInt()
        scroller.fling(0, span, 0, (velocity * WHEEL_FLING_DAMPING).toInt(), 0, 0, 0, span * 2)
        var lastLines = span / cellH
        val r = object : Runnable {
            override fun run() {
                if (!scroller.computeScrollOffset()) { flingRunnable = null; return }
                val nowLines = scroller.currY / cellH
                wheelTravel += nowLines - lastLines
                lastLines = nowLines
                emitWheelTravel(emu, x, y)
                if (scroller.isFinished) flingRunnable = null else postOnAnimation(this)
            }
        }
        flingRunnable = r
        postOnAnimation(r)
    }

    private fun cancelFling() {
        flingRunnable?.let { removeCallbacks(it) }
        flingRunnable = null
        if (!scroller.isFinished) scroller.abortAnimation()
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
                for (col in from..to) { val cell = cells[col]; if (!cell.trailing) row.appendCodePoint(cell.code) }
                when {
                    absLine == e.first -> sb.append(row.trimEnd())
                    // Auto-wrapped line: it continues on the next one, so no newline
                    // and no trimEnd (trailing spaces are real content of the full line)
                    isAbsLineWrapped(absLine, buf, total) -> sb.append(row)
                    else -> sb.appendLine(row.trimEnd())
                }
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
                for (cell in line) if (!cell.trailing) row.appendCodePoint(cell.code)
                if (buf.isScrollbackLineWrapped(i)) sb.append(row) else sb.appendLine(row.trimEnd())
            }
            for (row in 0 until buf.rows) {
                val line = StringBuilder()
                for (col in 0 until buf.columns) { val cell = buf.getCell(row, col); if (!cell.trailing) line.appendCodePoint(cell.code) }
                if (buf.isLineWrapped(row)) sb.append(line) else sb.appendLine(line.trimEnd())
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

    private fun isAbsLineWrapped(absLine: Int, buf: TerminalBuffer, totalScrollback: Int): Boolean =
        if (absLine < totalScrollback) buf.isScrollbackLineWrapped(absLine)
        else buf.isLineWrapped(absLine - totalScrollback)

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
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return TerminalInputConnection(this)
    }

    /**
     * Sends user input to the shell. Any keystroke snaps the view back to the bottom
     * (the live prompt), like every standard terminal — otherwise typing while scrolled
     * up in the history happens off-screen. Server output does NOT trigger this.
     */
    private fun emitInput(bytes: ByteArray) {
        if (scrollbackOffset != 0) {
            cancelFling()
            scrollbackOffset = 0
            invalidate()
        }
        onInput?.invoke(bytes)
    }

    /**
     * A real mouse wheel (USB or Bluetooth) arrives here, not through the touch path: one
     * notch is ±1 on [MotionEvent.AXIS_VSCROLL], fractional on precision wheels. Positive is
     * away from the user, i.e. back in the history — that direction is fixed by the hardware
     * convention, so [invertScroll], which is about which way a finger drags, doesn't apply.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_SCROLL) return super.onGenericMotionEvent(event)
        val emu = emulator ?: return false
        val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (v == 0f) return false
        wheelNotches += v
        val notches = wheelNotches.toInt()
        if (notches == 0) return true
        wheelNotches -= notches
        if (reportsWheel(emu)) {
            sendWheel(emu, notches, event.x, event.y)
        } else {
            cancelFling()
            scrollbackOffset = (scrollbackOffset + notches * LOCAL_WHEEL_LINES)
                .coerceIn(0, maxScrollOffset)
            invalidate()
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val bytes = keyEventToBytes(keyCode, event) ?: return super.onKeyDown(keyCode, event)
        emitInput(bytes)
        return true
    }

    private fun keyEventToBytes(keyCode: Int, event: KeyEvent): ByteArray? {
        val ctrl  = event.isCtrlPressed
        // AltGr composes characters (@ # [ ] on the Italian layout, …); it must not be
        // treated as the ESC-prefix Alt.
        val altGr = event.metaState and KeyEvent.META_ALT_RIGHT_ON != 0
        val alt   = event.isAltPressed && !altGr
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
                // KeyCharacterMap matches ctrl/alt/meta exactly: looking a key up with those
                // bits set finds no behavior and returns 0, which would drop every Ctrl+<key>
                // combo before it reaches the branches below. Strip them for the lookup, but
                // keep shift/caps-lock — and keep AltGr, which is a composition modifier.
                var bitsToClear = KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK
                if (!altGr) bitsToClear = bitsToClear or KeyEvent.META_ALT_MASK
                val ch = event.getUnicodeChar(event.metaState and bitsToClear.inv())
                if (ch == 0) return null
                val ctrlByte: Byte? = when {
                    !ctrl            -> null
                    ch == 0x20       -> 0            // Ctrl+Space → NUL
                    ch in 0x40..0x5F -> (ch - 0x40).toByte()
                    ch in 0x61..0x7A -> (ch - 0x60).toByte()
                    else             -> null
                }
                when {
                    ctrlByte != null -> if (alt) byteArrayOf(0x1B, ctrlByte) else byteArrayOf(ctrlByte)
                    alt              -> byteArrayOf(0x1B, ch.toByte())
                    else             -> ch.toChar().toString().toByteArray(Charsets.UTF_8)
                }
            }
        }
    }

    // --- Gesture: scroll, tap, long-press, pinch-zoom ---

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // Start each gesture with a clean accumulator so leftover fraction from a
            // previous drag can't nudge the view on the next touch-down.
            scrollRemainderY = 0f
            wheelTravel = 0f
            // A new touch stops any in-flight momentum (tap-to-halt, like a scroll view).
            cancelFling()
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (inSelectionMode) return false
            val emu = emulator ?: return false
            // Match onScroll's sign convention so momentum continues the drag direction.
            val v = if (invertScroll) -velocityY else velocityY
            if (reportsWheel(emu)) startWheelFling(emu, v, e2.x, e2.y) else startFling(v)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val emu = emulator ?: return false
            if (cellH <= 0f) return false
            scrollRemainderY += if (invertScroll) dy else -dy
            val lines = (scrollRemainderY / cellH).toInt()
            if (lines != 0) {
                scrollRemainderY -= lines * cellH
                if (reportsWheel(emu)) {
                    wheelTravel += lines
                    emitWheelTravel(emu, e2.x, e2.y)
                } else {
                    scrollbackOffset = (scrollbackOffset + lines).coerceIn(0, maxScrollOffset)
                    invalidate()
                }
            }
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
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (inSelectionMode) return false
            // Opt-in shell auto-completion (issue #4). One Tab completes; two Tabs
            // (sent back-to-back) make readline list the candidates. emitInput also
            // snaps the view back to the live prompt, like any other input.
            when (doubleTapAction) {
                AppPreferences.DOUBLE_TAP_TAB       -> emitInput(byteArrayOf(0x09))
                AppPreferences.DOUBLE_TAP_TAB_TWICE -> emitInput(byteArrayOf(0x09, 0x09))
                else -> return false
            }
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
            userScaled = true
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
    private inner class TerminalInputConnection(view: View) : BaseInputConnection(view, true) {
        // fullEditor=true keeps a real Editable in sync so the IME can read back text
        // via getTextBeforeCursor / getSurroundingText for spell-correction flows.
        // composingText mirrors what is currently in the terminal as composing chars,
        // so we can back-track and replace on commitText.
        private var composingText = ""
        // On Android ≤12 some keyboards call commitText THEN deleteSurroundingText for
        // spell correction (Editable-style: insert new text, then erase old region),
        // both inside the SAME batch edit. commitText already sent the backspaces for
        // composingText; we record how many so the redundant deleteSurroundingText in that
        // batch can subtract them and not double-delete. The count is scoped to the batch:
        // it is cleared when the batch closes (batchDepth → 0), so it can never bleed into a
        // later, independent user backspace (which arrives in its own separate batch).
        private var composingDeletedByCommit = 0
        private var batchDepth = 0

        override fun beginBatchEdit(): Boolean {
            batchDepth++
            return super.beginBatchEdit()
        }

        override fun endBatchEdit(): Boolean {
            val result = super.endBatchEdit()
            if (batchDepth > 0) batchDepth--
            // Outermost batch closed: a redundant in-batch deleteSurroundingText (if any) has
            // already consumed the count. Drop it so a later user backspace isn't swallowed.
            if (batchDepth == 0) composingDeletedByCommit = 0
            return result
        }

        // When composing restarts from empty, reconcile the new composing text with the word
        // already before the cursor. Returns the byte delta to send, or null when this is a
        // brand-new / unrelated word the caller should just append.
        private fun readoptWordBytes(newText: String): ByteArray? {
            val textBefore = getTextBeforeCursor(newText.length * 2 + 20, 0)?.toString() ?: ""
            val lastSpace = textBefore.lastIndexOf(' ')
            val wordBefore = if (lastSpace >= 0) textBefore.substring(lastSpace + 1) else textBefore
            // Only re-adopt when one string is a prefix of the other (Gboard shortening or
            // extending the same word, e.g. "ho" -> "h"). Unrelated text is left to the caller
            // so a fresh letter never erases a committed word the user is not editing.
            if (wordBefore.isEmpty() ||
                !(wordBefore.startsWith(newText) || newText.startsWith(wordBefore))) return null
            var common = 0
            while (common < wordBefore.length && common < newText.length
                   && wordBefore[common] == newText[common]) common++
            val toDelete = wordBefore.substring(common).toByteArray(Charsets.UTF_8).size
            val newSuffix = newText.substring(common)
            return ByteArray(toDelete) { 0x7F } + newSuffix.toByteArray(Charsets.UTF_8)
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composingDeletedByCommit = 0
            val newText = text?.toString() ?: ""
            val bytes: ByteArray
            val readoptBytes = if (composingText.isEmpty() && newText.isNotEmpty())
                readoptWordBytes(newText) else null
            if (readoptBytes != null) {
                // Composing started from empty and Gboard is re-adopting the word already before
                // the cursor (recomposing while backspacing, or undo-correction): emit only the
                // prefix-diff against that word. See readoptWordBytes for the prefix guard that
                // keeps a brand-new letter from erasing an unrelated committed word.
                bytes = readoptBytes
            } else {
                var common = 0
                while (common < composingText.length && common < newText.length
                       && composingText[common] == newText[common]) common++
                val toDelete = composingText.substring(common).toByteArray(Charsets.UTF_8).size
                val newSuffix = newText.substring(common)
                bytes = ByteArray(toDelete) { 0x7F } + newSuffix.toByteArray(Charsets.UTF_8)
            }
            if (bytes.isNotEmpty()) emitInput(bytes)
            composingText = newText
            return super.setComposingText(text, newCursorPosition)
        }

        override fun finishComposingText(): Boolean {
            composingText = ""
            composingDeletedByCommit = 0
            return super.finishComposingText()
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val deleted = composingText.toByteArray(Charsets.UTF_8).size
            // Apply cdc only when replacing composing text with a correction (non-empty text).
            // When deleting composing text via commitText(""), the subsequent
            // deleteSurroundingText targets a different character (the one before the composing
            // region, e.g. a space), so the cdc subtraction must not apply.
            composingDeletedByCommit = if (!text.isNullOrEmpty()) deleted else 0
            composingText = ""
            val addBytes = text?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
            val bytes = ByteArray(deleted) { 0x7F } + addBytes
            if (bytes.isNotEmpty()) emitInput(bytes)
            return super.commitText(text, newCursorPosition)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            // Subtract backspaces already sent by the preceding commitText (Android ≤12
            // spell-correction order: commitText first, then deleteSurroundingText).
            val effective = (beforeLength - composingDeletedByCommit).coerceAtLeast(0)
            composingDeletedByCommit = 0
            if (effective > 0) emitInput(ByteArray(effective) { 0x7F })
            // Do NOT call invalidateIme() here: on Android 13 it causes Gboard to abort
            // a multi-step spell correction (deleteSurroundingText + insert) mid-sequence.
            // invalidateIme() in commitText is sufficient to keep Gboard in sync.
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        // Android 13+ (Gboard on Android 16): replaces a range of editable text directly.
        @androidx.annotation.RequiresApi(33)
        override fun replaceText(start: Int, end: Int, text: CharSequence,
                                 newCursorPosition: Int,
                                 textAttribute: android.view.inputmethod.TextAttribute?): Boolean {
            // replaceText commits the text (framework uses composing=false) — it leaves NO live
            // composing span — so the correct post-state is composingText = "". Do NOT carry the
            // replacement forward as composing: on Gboards that drive smart-punctuation through
            // replaceText (e.g. Motorola Android 16: replaceText(" "→".")), a stale composingText
            // would be consumed by the next commitText(" "), which would backspace and eat the
            // just-inserted punctuation. A setComposingText that legitimately continues the word
            // after this is reconciled against the real Editable by readoptWordBytes.
            composingText = ""
            composingDeletedByCommit = 0
            val count = (end - start).coerceAtLeast(0)
            val bytes = ByteArray(count) { 0x7F } + text.toString().toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty()) emitInput(bytes)
            return super.replaceText(start, end, text, newCursorPosition, textAttribute)
        }

        // Silently reject rich content (images, stickers) — returning false would
        // trigger the system "App doesn't support images" toast on Android 12+.
        override fun commitContent(
            inputContentInfo: android.view.inputmethod.InputContentInfo,
            flags: Int,
            opts: android.os.Bundle?,
        ) = true

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // While a word is composing (span kept alive for auto-space/suggestions),
                // the IME drives deletions via setComposingText, so swallow only backspace
                // KeyEvents to avoid double-deleting. Any other key (Enter, arrows, …) ends
                // the word — it is already echoed in the terminal — so finalize the composing
                // state and let the key pass through (Enter would otherwise be swallowed).
                if (composingText.isNotEmpty()) {
                    if (event.keyCode == KeyEvent.KEYCODE_DEL) return true
                    composingText = ""
                    composingDeletedByCommit = 0
                    super.finishComposingText()
                }
                val bytes = keyEventToBytes(event.keyCode, event)
                if (bytes != null) {
                    emitInput(bytes)
                    if (event.keyCode == KeyEvent.KEYCODE_DEL) {
                        val ed = getEditable() ?: return true
                        val cur = android.text.Selection.getSelectionEnd(ed)
                        if (cur > 0) ed.delete(cur - 1, cur)
                    }
                    return true
                }
            }
            return super.sendKeyEvent(event)
        }
    }

    companion object {
        /**
         * Lines of travel per reported wheel notch. One notch per line keeps the content
         * moving roughly with the finger: tmux advances its copy-mode history about a line
         * per notch, so a coarser ratio here made a long swipe crawl.
         */
        private const val WHEEL_NOTCH_LINES = 1

        /** Lines of local scrollback per notch of a real wheel — the usual desktop step. */
        private const val LOCAL_WHEEL_LINES = 3

        /** Virtual span of a reported-wheel fling; caps how far one flick can carry. */
        private const val WHEEL_FLING_MAX_LINES = 400

        /**
         * Momentum applied to a reported-wheel flick, relative to the local scrollback fling.
         * A full-strength flick overshot badly here: the view stays still, so there is no
         * moving content to judge the speed against and even a gentle flick ran away.
         */
        private const val WHEEL_FLING_DAMPING = 0.7f

        private val DARK_FG  = Color.rgb(204, 204, 204)
        private val DARK_BG  = Color.BLACK
        private val LIGHT_FG = Color.rgb(51, 51, 51)
        private val LIGHT_BG = Color.WHITE
    }
}
