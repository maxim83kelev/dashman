package cz.kelev.dashman.services.voice.delete

import kotlin.random.Random

object PhrasePool {

    private fun pick(list: List<String>): String {
        return list[Random.nextInt(list.size)]
    }

    fun askWhatToDelete(): String = pick(
        listOf(
            "Что удалить?",
            "Назови часть напоминания.",
            "Давай уточним. Что именно снести?",
            "Слушаю. Что удалить?",
            "Ну? Что убираем?"
        )
    )

    fun nothingFound(): String = pick(
        listOf(
            "Ничего не нашёл.",
            "Пусто. Совсем пусто.",
            "Такого напоминания нет.",
            "Ноль совпадений.",
            "Не нашёл ничего похожего."
        )
    )

    fun found(count: Int): String = pick(
        listOf(
            "Нашёл $count напоминаний.",
            "Есть $count совпадений.",
            "Похоже на $count вариантов.",
            "Вот что нашёл: $count.",
            "$count совпадений."
        )
    )

    fun confirmDelete(): String = pick(
        listOf(
            "Удалить?",
            "Сносим?",
            "Точно удалить?",
            "Подтверди удаление.",
            "Говори 'да', если удаляем."
        )
    )

    fun deleted(): String = pick(
        listOf(
            "Готово.",
            "Удалил.",
            "Снесено.",
            "Больше этого нет.",
            "Напоминание ушло в небытие."
        )
    )
}