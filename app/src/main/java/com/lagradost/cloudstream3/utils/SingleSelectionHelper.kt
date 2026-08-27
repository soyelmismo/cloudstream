package com.lagradost.cloudstream3.utils

import android.app.Activity
import androidx.appcompat.app.AlertDialog

object SingleSelectionHelper {
    fun Activity?.showBottomDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        if (this == null) return
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(items.toTypedArray()) { dialog, which ->
                callback(which)
                dialog.dismiss()
            }
            .setOnDismissListener {
                dismissCallback()
            }
            .show()
    }

    fun Activity?.showDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        if (this == null) return
        AlertDialog.Builder(this)
            .setTitle(name)
            .setSingleChoiceItems(items.toTypedArray(), selectedIndex) { dialog, which ->
                callback(which)
                dialog.dismiss()
            }
            .setOnDismissListener {
                dismissCallback()
            }
            .show()
    }

    fun Activity?.showMultiDialog(
        items: List<String>,
        selectedIndex: List<Int>,
        name: String,
        dismissCallback: () -> Unit,
        callback: (List<Int>) -> Unit,
    ) {
        if (this == null) return
        val selected = BooleanArray(items.size) { selectedIndex.contains(it) }
        val currentSelections = selectedIndex.toMutableList()
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMultiChoiceItems(items.toTypedArray(), selected) { _, which, isChecked ->
                if (isChecked) {
                    currentSelections.add(which)
                } else {
                    currentSelections.remove(which)
                }
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                callback(currentSelections)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                dismissCallback()
            }
            .show()
    }
}
