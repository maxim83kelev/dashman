package cz.kelev.dashman.services.voice.postpone

/**
 * Фразы Дашмана для диалога переноса напоминания.
 * Стиль: саркастичный, прямой, без сюсюканья.
 */
object PostponePhrases {

    private val askWhatPhrases = listOf(
        "Что перенести? Говори конкретно.",
        "Я не телепат. Что именно переносим?",
        "Какое напоминание? У тебя их может быть несколько.",
        "Уточни — что переносим?",
        "Слушаю. Что откладываем на потом?"
    )

    private val askWhatAgainPhrases = listOf(
        "Не расслышал. Ещё раз — что переносим?",
        "Попробуй снова. Что за напоминание?",
        "Не понял. Повтори название.",
        "Снова. Что именно нужно перенести?"
    )

    private val askWhenPhrases = listOf(
        "На когда переносим?",
        "Новое время — говори.",
        "Когда поставить?",
        "Называй дату и время.",
        "На когда откладываем?"
    )

    private val askWhenAgainPhrases = listOf(
        "Не разобрал время. Повтори — на когда?",
        "Время не распознал. Попробуй ещё раз.",
        "Не понял. Скажи дату или время нормально.",
        "Что-то не то. Когда именно?"
    )

    private val askWhenTimePhrases = listOf(
        "Дату понял. На какое время?",
        "Дата есть. Время — говори.",
        "Число принял. Во сколько?",
        "Дату зафиксировал. Время уточни."
    )

    private val askWhenTimeAgainPhrases = listOf(
        "Время не разобрал. Повтори — во сколько?",
        "Не понял время. Ещё раз.",
        "Время некорректное. Скажи нормально — во сколько?",
        "Не распознал. Время — цифрами или словами."
    )

    private val notFoundPhrases = listOf(
        "Ничего похожего нет. Может, уже удалено?",
        "Не нашёл. Попробуй другое слово.",
        "Ноль совпадений. Уточни название.",
        "Нет такого. Скажи иначе."
    )

    private val cancelledPhrases = listOf(
        "Окей, отменяю.",
        "Понял. Ничего не трогаю.",
        "Отменено. Напоминание осталось как было.",
        "Ладно, забыли."
    )

    private val donePhrases = listOf(
        "Перенёс.",
        "Готово. Время обновлено.",
        "Сделано. Перенос зафиксирован.",
        "Есть. Новое время поставлено.",
        "Окей. Напоминание перенесено."
    )

    private val internalErrorPhrases = listOf(
        "Что-то пошло не так внутри. Начни сначала.",
        "Потерял контекст. Попробуй ещё раз.",
        "Сбой. Повтори команду."
    )

    // ─── Публичное API ────────────────────────────────────────────────────────

    fun askWhat(): String = askWhatPhrases.random()

    fun askWhatAgain(): String = askWhatAgainPhrases.random()

    fun askWhen(): String = askWhenPhrases.random()

    fun askWhenAgain(): String = askWhenAgainPhrases.random()

    fun askWhenTime(): String = askWhenTimePhrases.random()

    fun askWhenTimeAgain(): String = askWhenTimeAgainPhrases.random()

    fun notFound(): String = notFoundPhrases.random()

    fun cancelled(): String = cancelledPhrases.random()

    fun done(): String = donePhrases.random()

    fun internalError(): String = internalErrorPhrases.random()

    fun found(label: String): String {
        val starters = listOf(
            "Нашёл:",
            "Вот оно:",
            "Есть такое:"
        )
        return "${starters.random()} $label."
    }

    fun foundMultiple(count: Int, firstLabel: String): String {
        val starters = listOf(
            "Нашёл $count совпадений. Беру первое:",
            "Совпадений $count, перенесу самое близкое:",
            "Несколько подходит. Беру:"
        )
        return "${starters.random()} $firstLabel."
    }
}