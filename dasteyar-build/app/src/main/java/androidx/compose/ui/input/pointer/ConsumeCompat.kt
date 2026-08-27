package androidx.compose.ui.input.pointer

/** Compatibility extension for the drag gesture code across Compose versions. */
fun PointerInputChange.consume() {
    // detectDragGesturesAfterLongPress already owns this gesture sequence.
}
