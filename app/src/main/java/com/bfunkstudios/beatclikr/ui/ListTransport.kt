package com.bfunkstudios.beatclikr.ui

internal fun <T, ID> currentItemIndex(
    items: List<T>,
    currentId: ID?,
    idOf: (T) -> ID
): Int? = currentId
    ?.let { id -> items.indexOfFirst { idOf(it) == id } }
    ?.takeIf { it >= 0 }

internal fun <T, ID> resumeItem(
    items: List<T>,
    currentId: ID?,
    idOf: (T) -> ID
): T? = currentItemIndex(items, currentId, idOf)?.let(items::get) ?: items.firstOrNull()

internal fun <T, ID> adjacentItem(
    items: List<T>,
    currentId: ID?,
    offset: Int,
    idOf: (T) -> ID
): T? = currentItemIndex(items, currentId, idOf)?.let { items.getOrNull(it + offset) }
