package com.sshborg.ui.extrabar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.BarFontScale
import com.sshborg.data.ExtraBar
import com.sshborg.data.ExtraBarPresets
import com.sshborg.data.ExtraBarRow
import com.sshborg.data.ExtraKeyDef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Bar list: select, duplicate (presets too — the way to start customising), delete. */
class ExtraBarsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = (app as SshBorgApp).appPreferences

    val customBars: StateFlow<List<ExtraBar>> =
        prefs.customExtraBars.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedId: StateFlow<String> =
        prefs.extraBarSelectedId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExtraBarPresets.STANDARD)

    fun select(id: String) {
        viewModelScope.launch { prefs.setExtraBarSelectedId(id) }
    }

    /** Copies [bar] (preset or custom) into a new custom bar named [name]; returns its id. */
    fun duplicate(bar: ExtraBar, name: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val copy = bar.copy(id = newId(), name = name)
            prefs.setCustomExtraBars(prefs.customExtraBars.first() + copy)
            onDone(copy.id)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            prefs.setCustomExtraBars(prefs.customExtraBars.first().filterNot { it.id == id })
            // Deleting the active bar falls back to the standard preset explicitly, so
            // Settings shows what the terminal renders.
            if (prefs.extraBarSelectedId.first() == id) prefs.setExtraBarSelectedId(ExtraBarPresets.STANDARD)
        }
    }

    companion object {
        fun newId() = ExtraBar.CUSTOM_PREFIX + UUID.randomUUID().toString()
    }
}

/**
 * Editor state for one custom bar: an in-memory draft plus the (row, index) selection.
 * Nothing is persisted until [save]; the ViewModel outlives rotation so the draft does too.
 */
class ExtraBarEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = (app as SshBorgApp).appPreferences

    private val _draft = MutableStateFlow<ExtraBar?>(null)
    val draft: StateFlow<ExtraBar?> = _draft.asStateFlow()

    private val _selected = MutableStateFlow<Pair<Int, Int>?>(null)
    val selected: StateFlow<Pair<Int, Int>?> = _selected.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private var loadedFor: String? = null

    /** Loads [id] once (or starts a fresh bar for [NEW_ID]); later calls are no-ops. */
    fun load(id: String, newName: String) {
        if (loadedFor == id) return
        loadedFor = id
        viewModelScope.launch {
            _draft.value =
                if (id == NEW_ID) ExtraBar(
                    ExtraBarsViewModel.newId(), newName,
                    listOf(ExtraBarRow(emptyList(), fit = true)), BarFontScale.MEDIUM,
                )
                else prefs.customExtraBars.first().find { it.id == id }
                    ?: ExtraBarPresets.byId(id)?.copy(id = ExtraBarsViewModel.newId(), name = newName)
        }
    }

    fun save(onDone: () -> Unit) {
        val bar = _draft.value ?: return
        viewModelScope.launch {
            val others = prefs.customExtraBars.first().filterNot { it.id == bar.id }
            // Keep the original position when re-saving an existing bar.
            val existingIdx = prefs.customExtraBars.first().indexOfFirst { it.id == bar.id }
            val list = if (existingIdx >= 0) others.toMutableList().apply { add(existingIdx, bar) } else others + bar
            prefs.setCustomExtraBars(list)
            _dirty.value = false
            onDone()
        }
    }

    // ── draft edits ────────────────────────────────────────────────────────

    private fun update(f: (ExtraBar) -> ExtraBar) {
        _draft.value = _draft.value?.let(f)
        _dirty.value = true
    }

    private fun updateRows(f: (MutableList<ExtraBarRow>) -> Unit) = update { b ->
        b.copy(rows = b.rows.toMutableList().apply(f))
    }

    fun setName(name: String) = update { it.copy(name = name) }
    fun setFontScale(scale: BarFontScale) = update { it.copy(fontScale = scale) }

    fun select(pos: Pair<Int, Int>?) {
        _selected.value = if (pos == _selected.value) null else pos
    }

    fun setRowFit(row: Int, fit: Boolean) = updateRows { it[row] = it[row].copy(fit = fit) }

    fun addRow() {
        val bar = _draft.value ?: return
        if (bar.rows.size >= ExtraBar.MAX_ROWS) return
        updateRows { it.add(ExtraBarRow(emptyList(), fit = true)) }
    }

    fun removeRow(row: Int) {
        val bar = _draft.value ?: return
        if (bar.rows.size <= 1) return
        updateRows { it.removeAt(row) }
        _selected.value = null
    }

    /** Inserts after the selection, or at the end of the last row when nothing is selected. */
    fun insertKey(key: ExtraKeyDef) {
        val bar = _draft.value ?: return
        val (r, i) = _selected.value ?: (bar.rows.lastIndex to bar.rows.last().keys.lastIndex)
        updateRows { rows ->
            val keys = rows[r].keys.toMutableList().apply { add(i + 1, key) }
            rows[r] = rows[r].copy(keys = keys)
        }
        _selected.value = r to i + 1
    }

    fun replaceKey(key: ExtraKeyDef) {
        val (r, i) = _selected.value ?: return
        updateRows { rows ->
            rows[r] = rows[r].copy(keys = rows[r].keys.toMutableList().apply { set(i, key) })
        }
    }

    fun removeKey() {
        val (r, i) = _selected.value ?: return
        updateRows { rows ->
            rows[r] = rows[r].copy(keys = rows[r].keys.toMutableList().apply { removeAt(i) })
        }
        val row = _draft.value?.rows?.getOrNull(r) ?: return
        _selected.value = if (row.keys.isEmpty()) null else r to i.coerceAtMost(row.keys.lastIndex)
    }

    fun moveKey(delta: Int) {
        val (r, i) = _selected.value ?: return
        val row = _draft.value?.rows?.getOrNull(r) ?: return
        val j = i + delta
        if (j !in row.keys.indices) return
        updateRows { rows ->
            val keys = rows[r].keys.toMutableList()
            keys[i] = keys[j].also { keys[j] = keys[i] }
            rows[r] = rows[r].copy(keys = keys)
        }
        _selected.value = r to j
    }

    /** Moves the selected key to the same index (or the end) of the row above/below. */
    fun moveKeyToRow(delta: Int) {
        val (r, i) = _selected.value ?: return
        val bar = _draft.value ?: return
        val t = r + delta
        if (t !in bar.rows.indices) return
        val key = bar.rows[r].keys[i]
        updateRows { rows ->
            rows[r] = rows[r].copy(keys = rows[r].keys.toMutableList().apply { removeAt(i) })
            val target = rows[t].keys.toMutableList()
            val at = i.coerceAtMost(target.size)
            target.add(at, key)
            rows[t] = rows[t].copy(keys = target)
        }
        _selected.value = t to i.coerceAtMost(bar.rows[t].keys.size)
    }

    companion object {
        const val NEW_ID = "new"
    }
}
