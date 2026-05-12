package cz.kelev.dashman.services.voice.edit

/**
 * Фразы Дашмана для диалога редактирования напоминания.
 * Стиль: саркастичный, прямой, без сюсюканья.
 */
object EditPhrases {

    private val askWhatPhrases = listOf(
        "Что изменить? Говори.",
        "Я не телепат. Какое напоминание правим?",
        "Называй. Что редактируем?",
        "Уточни — что именно нужно изменить?",
        "Слушаю. Какое напоминание трогаем?"
    )

    private val askWhatAgainPhrases = listOf(
        "Не расслышал. Ещё раз — что меняем?",
        "Попробуй снова. Название напоминания.",
        "Не понял. Повтори — что редактируем?",
        "Снова. Какое напоминание?"
    )

    private val askNewTextPhrases = listOf(
        "Как звучит новая версия?",
        "Диктуй новый текст.",
        "Говори — что должно быть написано?",
        "Новый текст — слушаю.",
        "Как теперь будет звучать?"
    )

    private val askNewTextAgainPhrases = listOf(
        "Не расслышал. Ещё раз — новый текст?",
        "Пусто. Диктуй снова.",
        "Не понял. Повтори новую версию.",
        "Снова. Что должно быть написано?"
    )

    private val askChangeTimePhrases = listOf(
        "Время оставляем или тоже меняем?",
        "Текст обновил. Время трогаем?",
        "С текстом готово. Дату менять будем?",
        "Записал. Время оставляем как есть?"
    )

    private val askWhenPhrases = listOf(
        "На когда?",
        "Новое время — говори.",
        "Называй дату и время.",
        "Когда поставить?",
        "Дату и время — давай."
    )

    private val askWhenAgainPhrases = listOf(
        "Не разобрал время. Повтори — на когда?",
        "Время не распознал. Попробуй ещё раз.",
        "Не понял. Скажи дату или время нормально.",
        "Что-то не то. Когда именно?"
    )

    private val clarifyWhenPhrases = listOf(
        "Уточни — дату и время.",
        "Конкретнее. Когда именно?",
        "Дата и время. Говори.",
        "Назови дату и время нормально."
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

    private val doneTextPhrases = listOf(
        "Готово. Текст изменил.",
        "Записал новый текст.",
        "Обновил. Сохранено.",
        "Сделано. Новая версия зафиксирована."
    )

    private val doneTimePhrases = listOf(
        "Зафиксировал.",
        "Готово. Время обновлено.",
        "Есть. Новое время поставлено.",
        "Окей. Всё обновлено."
    )

    private val internalErrorPhrases = listOf(
        "Что-то пошло не так внутри. Начни сначала.",
        "Потерял контекст. Попробуй ещё раз.",
        "Сбой. Повтори команду."
    )

    // ─── Публичное API ────────────────────────────────────────────────────────

    fun askWhat(): String = askWhatPhrases.random()

    fun askWhatAgain(): String = askWhatAgainPhrases.random()

    fun askNewText(): String = askNewTextPhrases.random()

    fun askNewTextAgain(): String = askNewTextAgainPhrases.random()

    fun askChangeTime(): String = askChangeTimePhrases.random()

    fun askWhen(): String = askWhenPhrases.random()

    fun askWhenAgain(): String = askWhenAgainPhrases.random()

    fun clarifyWhen(): String = clarifyWhenPhrases.random()

    fun notFound(): String = notFoundPhrases.random()

    fun cancelled(): String = cancelledPhrases.random()

    fun doneText(): String = doneTextPhrases.random()

    fun doneTime(): String = doneTimePhrases.random()

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
            "Совпадений $count, редактирую ближайшее:",
            "Несколько подходит. Беру:"
        )
        return "${starters.random()} $firstLabel."
    }
}