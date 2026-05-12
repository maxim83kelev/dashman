package cz.kelev.dashman

import cz.kelev.dashman.services.filter.ReminderFilter
import kotlin.random.Random

object FilterAnswerPhrases {

    fun randomFor(filter: ReminderFilter, count: Int? = null): String {
        if (count != null && count <= 0) {
            return emptyVariantsFor(filter).random(Random)
        }

        val suffix = countSuffix(count)

        val variants = when (filter) {
            is ReminderFilter.All -> listOf(
                "Показываю всё.$suffix",
                "Снял фильтр. Вот весь список.$suffix",
                "Ладно, держи всё сразу.$suffix",
                "Вот всё, что ты успел накидать.$suffix",
                "Полный список перед тобой.$suffix",

                "Окей, без фильтров. Смотри весь бардак.$suffix",
                "Сбросил ограничения. Любуйся всем списком.$suffix",
                "Вот всё скопом, как ты любишь.$suffix",
                "Никакой магии, просто весь список.$suffix",
                "Держи всё разом. Не потеряйся.$suffix",

                "Ну всё, фильтр снят. Теперь не ной, что много.$suffix",
                "Вот тебе весь список, герой планирования.$suffix"
            )

            is ReminderFilter.Today -> listOf(
                "Вот что у тебя сегодня.$suffix",
                "На сегодня у тебя такой расклад.$suffix",
                "Сегодня у тебя вот это.$suffix",
                "Собрал всё на сегодня.$suffix",
                "Вот список на сегодня.$suffix",

                "Смотри, чем ты загрузил сегодняшний день.$suffix",
                "Сегодняшний набор задач готов.$suffix",
                "Вот чем тебя сегодня жизнь порадует.$suffix",
                "На сегодня всё вот так.$suffix",
                "Вот твои сегодняшние напоминания.$suffix",

                "Сегодня у тебя движ вот такой.$suffix",
                "Ну что, вот твой сегодняшний фронт работ.$suffix"
            )

            is ReminderFilter.Tomorrow -> listOf(
                "Вот что у тебя на завтра.$suffix",
                "Завтрашний список готов.$suffix",
                "На завтра у тебя вот это.$suffix",
                "Собрал напоминания на завтра.$suffix",
                "Вот чем занят завтрашний день.$suffix",

                "Смотри, что тебя ждёт завтра.$suffix",
                "Завтра у тебя не пусто.$suffix",
                "Вот расклад на завтра.$suffix",
                "Показываю всё, что у тебя на завтра.$suffix",

                "Завтра уже подкралось, вот список.$suffix",
                "На завтра ты себе уже напридумывал вот это.$suffix"
            )

            is ReminderFilter.DayAfterTomorrow -> listOf(
                "Вот что у тебя послезавтра.$suffix",
                "На послезавтра у тебя вот такой набор.$suffix",
                "Собрал всё на послезавтра.$suffix",
                "Послезавтрашний список готов.$suffix",

                "Да, и послезавтра ты тоже занят.$suffix",
                "Вот чем забито послезавтра.$suffix"
            )

            is ReminderFilter.InDays -> listOf(
                "Вот что у тебя через ${filter.daysAhead} дн.$suffix",
                "Список на ${filter.daysAhead} дн. вперёд готов.$suffix",
                "Показываю, что у тебя через ${filter.daysAhead} дн.$suffix",
                "На ${filter.daysAhead} дн. вперёд у тебя вот это.$suffix",

                "Да, ты и туда уже себе дел накидал.$suffix",
                "Через ${filter.daysAhead} дн. у тебя тоже не пусто.$suffix"
            )

            is ReminderFilter.ThisWeek -> listOf(
                "Вот что у тебя на этой неделе.$suffix",
                "Неделя у тебя выглядит так.$suffix",
                "Собрал всё на эту неделю.$suffix",
                "Вот твой недельный расклад.$suffix",
                "Показываю всё, что висит на этой неделе.$suffix",

                "Неделя уже расписана, держи список.$suffix",
                "Вот чем ты занял эту неделю.$suffix",
                "Недельный движ такой.$suffix",

                "Ну что, неделя у тебя не скучная.$suffix",
                "Вот твой недельный марафон, чемпион.$suffix"
            )
            
            is ReminderFilter.ThisMonth -> listOf(
                "Вот что у тебя в этом месяце.$suffix",
                "Месяц у тебя выглядит вот так.$suffix",
                "Собрал всё на этот месяц.$suffix",
                "Показываю месячный список.$suffix",
                "Вот твои дела в этом месяце.$suffix",

                "Месяц ты себе уже украсил как мог.$suffix",
                "Вот чем забит этот месяц.$suffix",
                "На этот месяц у тебя такая картина.$suffix",

                "Ну да, месяц ты тоже решил не щадить.$suffix",
                "Вот твой месячный замес.$suffix"
            )

            is ReminderFilter.ByMonth -> listOf(
                "Вот что у тебя в ${monthLabel(filter)}.$suffix",
                "Собрал всё на ${monthLabel(filter)}.$suffix",
                "На ${monthLabel(filter)} у тебя вот такой список.$suffix",
                "Показываю всё, что у тебя запланировано на ${monthLabel(filter)}.$suffix",

                "Да, даже ${monthLabel(filter)} у тебя уже забит.$suffix",
                "Вот твой расклад на ${monthLabel(filter)}.$suffix"
            )
            
            is ReminderFilter.ThisWeekend -> listOf(
                "Вот что у тебя на этих выходных.$suffix",
                "Выходные у тебя заняты вот этим.$suffix",
                "Собрал всё на ближайшие выходные.$suffix",
                "Вот твой список на выходные.$suffix",

                "Даже выходные у тебя не без приключений.$suffix",
                "На выходных ты себе устроил вот это.$suffix",

                "Ну конечно, отдыхать спокойно ты не будешь.$suffix",
                "Вот твой план на выходные, трудоголик.$suffix"
            )

            is ReminderFilter.ByWeekday -> listOf(
                "Вот что у тебя на ${weekdayLabel(filter)}.$suffix",
                "Список на ${weekdayLabel(filter)} готов.$suffix",
                "На ${weekdayLabel(filter)} у тебя вот это.$suffix",
                "Собрал напоминания на ${weekdayLabel(filter)}.$suffix",

                "Да, даже на ${weekdayLabel(filter)} у тебя всё схвачено.$suffix",
                "Вот чем у тебя занят ${weekdayLabel(filter)}.$suffix"
            )

            is ReminderFilter.ByExactDate -> listOf(
                "Вот что у тебя на ${formatDate(filter)}.$suffix",
                "На ${formatDate(filter)} у тебя вот это.$suffix",
                "Собрал напоминания на ${formatDate(filter)}.$suffix",
                "Показываю всё на ${formatDate(filter)}.$suffix",

                "На ${formatDate(filter)} ты себе уже всё придумал.$suffix",
                "Вот твой набор на ${formatDate(filter)}.$suffix",

                "Ну что, до ${formatDate(filter)} ты тоже добрался.$suffix",
                "На ${formatDate(filter)} у тебя бодрый ассортимент.$suffix"
            )

            is ReminderFilter.PartOfDay.Morning -> listOf(
                "Вот всё, что у тебя утром.$suffix",
                "Утренний список готов.$suffix",
                "Собрал всё на утро.$suffix",
                "Вот твои утренние напоминания.$suffix",

                "Утро у тебя, как обычно, с сюрпризами.$suffix",
                "Вот чем начинается твой день.$suffix"
            )

            is ReminderFilter.PartOfDay.Afternoon -> listOf(
                "Вот что у тебя днём.$suffix",
                "Дневной расклад такой.$suffix",
                "Собрал всё на день.$suffix",
                "Вот твои дневные напоминания.$suffix",

                "Днём ты тоже без дела не сидишь.$suffix",
                "Вот что у тебя на дневную смену.$suffix"
            )

            is ReminderFilter.PartOfDay.Evening -> listOf(
                "Вот что у тебя вечером.$suffix",
                "Вечером у тебя вот такой движ.$suffix",
                "Собрал всё на вечер.$suffix",
                "Вот твои вечерние напоминания.$suffix",

                "Вечер у тебя спокойным не будет.$suffix",
                "Вот чем у тебя занят вечер.$suffix",

                "Ну да, даже вечером расслабиться не судьба.$suffix",
                "Вечерний замес у тебя вот такой.$suffix"
            )

            is ReminderFilter.PartOfDay.Night -> listOf(
                "Вот что у тебя ночью.$suffix",
                "Ночной список готов.$suffix",
                "Собрал всё на ночь.$suffix",
                "Вот твои ночные напоминания.$suffix",

                "Ночью у тебя тоже какая-то движуха.$suffix",
                "Вот чем занята твоя ночь.$suffix",
                "Ну конечно, даже ночью покоя не будет.$suffix",
                "Ночная программа у тебя вот такая.$suffix"
            )

            is ReminderFilter.Combined -> combinedVariants(filter, count)
                is ReminderFilter.ByIds -> listOf("Вот что нашёл.")

            is ReminderFilter.ByPriority -> when (filter.priority) {
                "important" -> listOf(
                    "Вот твои важные дела.$suffix",
                    "Важное — держи.$suffix",
                    "Собрал всё важное.$suffix",
                    "Вот что у тебя в приоритете.$suffix",
                    "Важняк весь тут.$suffix"
                )
                "critical" -> listOf(
                    "Срочное — всё здесь.$suffix",
                    "Вот что горит.$suffix",
                    "Критичное на экране.$suffix",
                    "Держи красную зону.$suffix"
                )
                else -> listOf("Вот по приоритету.$suffix")
            }   
        }

        return variants.random(Random)
    }

    private fun emptyVariantsFor(filter: ReminderFilter): List<String> {
        return when (filter) {
            is ReminderFilter.All -> listOf(
                "Список пуст. Прямо даже подозрительно.",
                "У тебя сейчас вообще ничего не запланировано.",
                "Пусто. Можешь сделать вид, что ты свободный человек.",
                "Ничего нет. Редкий, почти мифический момент."
            )

            is ReminderFilter.Today -> listOf(
                "На сегодня у тебя ничего не запланировано.",
                "Сегодня список пуст.",
                "Сегодня у тебя чисто.",
                "На сегодня пусто, можешь выдохнуть."
            )

            is ReminderFilter.Tomorrow -> listOf(
                "На завтра у тебя ничего нет.",
                "Завтра пока пусто.",
                "На завтра ничего не висит.",
                "Завтрашний день пока свободен."
            )

            is ReminderFilter.DayAfterTomorrow -> listOf(
                "На послезавтра у тебя ничего не запланировано.",
                "Послезавтра пока пусто.",
                "На послезавтра дел не найдено."
            )

            is ReminderFilter.InDays -> listOf(
                "Через ${filter.daysAhead} дн. у тебя ничего не найдено.",
                "На этот день пока пусто.",
                "Через ${filter.daysAhead} дн. у тебя свободно."
            )

            is ReminderFilter.ThisWeek -> listOf(
                "На этой неделе у тебя ничего не запланировано.",
                "Неделя пока пустая.",
                "На эту неделю задач не найдено.",
                "На неделе у тебя пока тишина."
            )

            is ReminderFilter.ThisMonth -> listOf(
                "В этом месяце у тебя пока ничего нет.",
                "На этот месяц список пуст.",
                "В месяце пока тишина.",
                "На этот месяц у тебя ничего не запланировано."
            )

            is ReminderFilter.ByMonth -> listOf(
                "На ${monthLabel(filter)} у тебя ничего не запланировано.",
                "На ${monthLabel(filter)} список пуст.",
                "На ${monthLabel(filter)} дел не найдено.",
                "${monthLabel(filter)} у тебя пока свободен."
            )

            is ReminderFilter.ThisWeekend -> listOf(
                "На выходных у тебя пока ничего нет.",
                "Эти выходные пока свободны.",
                "На выходные дел не найдено.",
                "Выходные пока пустые. Не испорть это."
            )

            is ReminderFilter.ByWeekday -> listOf(
                "На ${weekdayLabel(filter)} у тебя ничего не запланировано.",
                "На ${weekdayLabel(filter)} список пуст.",
                "На ${weekdayLabel(filter)} дел не найдено."
            )

            is ReminderFilter.ByExactDate -> listOf(
                "На ${formatDate(filter)} у тебя ничего не запланировано.",
                "На ${formatDate(filter)} пусто.",
                "На эту дату дел не найдено."
            )

            is ReminderFilter.PartOfDay.Morning -> listOf(
                "Утром у тебя ничего не запланировано.",
                "На утро список пуст.",
                "Утро пока свободно."
            )

            is ReminderFilter.PartOfDay.Afternoon -> listOf(
                "Днём у тебя ничего не запланировано.",
                "На день список пуст.",
                "Днём пока свободно."
            )

            is ReminderFilter.PartOfDay.Evening -> listOf(
                "Вечером у тебя ничего не запланировано.",
                "На вечер пусто.",
                "Вечер пока свободен."
            )

            is ReminderFilter.PartOfDay.Night -> listOf(
                "Ночью у тебя ничего не запланировано.",
                "На ночь список пуст.",
                "Ночью дел не найдено. И правильно."
            )

            is ReminderFilter.ByIds -> listOf("По запросу ничего не найдено.")

            is ReminderFilter.ByPriority -> when (filter.priority) {
                "important" -> listOf(
                    "Важных дел не найдено. Редкий момент.",
                    "Важного ничего нет. Живёшь без напряга.",
                    "Важняка нет. Наслаждайся."
                )
                "critical" -> listOf(
                    "Срочного ничего нет. Можно выдохнуть.",
                    "Красная зона пуста. Хорошие новости."
                )
                else -> listOf("По приоритету ничего не найдено.")
            }

                is ReminderFilter.Combined -> {
                    val base = when (filter.base) {
                    is ReminderFilter.Today -> "сегодня"
                    is ReminderFilter.Tomorrow -> "завтра"
                    is ReminderFilter.DayAfterTomorrow -> "послезавтра"
                    is ReminderFilter.ThisWeek -> "на этой неделе"
                    is ReminderFilter.ThisMonth -> "в этом месяце"
                    is ReminderFilter.ThisWeekend -> "на этих выходных"
                    is ReminderFilter.ByExactDate -> formatDate(filter.base)
                    is ReminderFilter.ByWeekday -> weekdayLabel(filter.base)
                    is ReminderFilter.InDays -> "через ${filter.base.daysAhead} дн."
                    else -> "на этот период"
                }
                val part = when (filter.partOfDay) {
                    is ReminderFilter.PartOfDay.Morning -> "утром"
                    is ReminderFilter.PartOfDay.Afternoon -> "днём"
                    is ReminderFilter.PartOfDay.Evening -> "вечером"
                    is ReminderFilter.PartOfDay.Night -> "ночью"
                }

                listOf(
                    "У тебя $base $part ничего не запланировано.",
                    "На $base $part список пуст.",
                    "На $base $part дел не найдено.",
                    "$base $part у тебя свободно."
                )
            }
        }
    }

    private fun combinedVariants(filter: ReminderFilter.Combined, count: Int?): List<String> {
        val suffix = countSuffix(count)
        val base = when (filter.base) {
            is ReminderFilter.Today -> "сегодня"
            is ReminderFilter.Tomorrow -> "завтра"
            is ReminderFilter.DayAfterTomorrow -> "послезавтра"
            is ReminderFilter.ThisWeek -> "на этой неделе"
            is ReminderFilter.ThisMonth -> "в этом месяце"
            is ReminderFilter.ThisWeekend -> "на этих выходных"
            is ReminderFilter.ByExactDate -> formatDate(filter.base)
            is ReminderFilter.ByWeekday -> weekdayLabel(filter.base)
            is ReminderFilter.InDays -> "через ${filter.base.daysAhead} дн."
            else -> "на этот период"
        }

        val part = when (filter.partOfDay) {
            is ReminderFilter.PartOfDay.Morning -> "утром"
            is ReminderFilter.PartOfDay.Afternoon -> "днём"
            is ReminderFilter.PartOfDay.Evening -> "вечером"
            is ReminderFilter.PartOfDay.Night -> "ночью"
        }

        return listOf(
            "Вот что у тебя $base $part.$suffix",
            "Собрал всё, что у тебя $base $part.$suffix",
            "На $base $part у тебя вот такой список.$suffix",
            "Смотри, что у тебя $base $part.$suffix",

            "Да, $base $part ты тоже занят.$suffix",
            "Вот твой расклад: $base $part.$suffix",

            "Ну что, $base $part отдых не завезли.$suffix",
            "На $base $part у тебя бодрый набор дел.$suffix"
        )
    }

    private fun countSuffix(count: Int?): String {
        if (count == null) return ""
        return when {
            count <= 0 -> " Пусто."
            count == 1 -> " Нашёл 1 напоминание."
            count in 2..4 -> " Нашёл $count напоминания."
            else -> " Нашёл $count напоминаний."
        }
    }

    private fun weekdayLabel(filter: ReminderFilter.ByWeekday): String {
        return when (filter.dayOfWeek.value) {
            1 -> "понедельник"
            2 -> "вторник"
            3 -> "среду"
            4 -> "четверг"
            5 -> "пятницу"
            6 -> "субботу"
            7 -> "воскресенье"
            else -> "нужный день"
        }
    }

    private fun monthLabel(filter: ReminderFilter.ByMonth): String {
        val month = when (filter.month) {
            1 -> "январе"
            2 -> "феврале"
            3 -> "марте"
            4 -> "апреле"
            5 -> "мае"
            6 -> "июне"
            7 -> "июле"
            8 -> "августе"
            9 -> "сентябре"
            10 -> "октябре"
            11 -> "ноябре"
            12 -> "декабре"
            else -> "этом месяце"
        }
        return "$month ${filter.year}"
    }

    private fun formatDate(filter: ReminderFilter.ByExactDate): String {
        val d = filter.date
        return "%02d.%02d".format(d.dayOfMonth, d.monthValue)
    }
}