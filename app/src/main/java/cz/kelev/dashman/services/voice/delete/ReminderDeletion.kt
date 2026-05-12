package cz.kelev.dashman.services.voice.delete

import cz.kelev.dashman.storage.ReminderEntity

object ReminderDeletion {

    /**
     * Единая “физика” удаления: ищем сущность по id и вызываем deleter.
     * Возвращает true, если нашли и попытались удалить.
     */
    fun deleteById(
        current: List<ReminderEntity>,
        id: Long,
        deleter: (ReminderEntity) -> Unit
    ): Boolean {
        val entity = current.firstOrNull { it.id == id } ?: return false
        deleter(entity)
        return true
    }
}