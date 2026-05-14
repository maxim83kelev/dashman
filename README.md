⚡ Dashman
Voice-powered reminder app for Android. No tapping. Just talk.

Built by one person, between construction shifts. No corporate team. No QA department. Just code and stubbornness.


🇬🇧 English
What is Dashman?
Dashman is an Android reminder app controlled entirely by voice. You speak naturally — Dashman figures out the time, date, and repeat rules on its own. No forms, no tapping, no templates.
Press. Talk. Done. It reminds you when it matters.
Features

🎙️ Full voice control — create, edit, move, delete reminders by voice
🕐 Smart time parser — understands natural language: "in 3 days", "next Monday at 10", "every weekday at 9am"
🔁 Repeat rules — daily, weekly, weekdays, weekends, custom intervals, date ranges
🔍 Voice search & filter — "show me everything for tomorrow evening"
🗣️ Voice confirmation — Dashman speaks back what it just did
🔑 License system — freemium model with device-based activation via Telegram bot
🔄 Auto-updates — checks GitHub Releases, prompts in-app
💾 Backup & restore — via Android Storage Access Framework (SAF)
🌙 Dark theme — always, no toggle needed

Tech Stack
LayerTechnologyLanguageKotlinUIJetpack ComposeArchitectureMVVMLocal DBRoom (SQLite)Voice recognitionAndroid SpeechRecognizerHotword detectionPicovoice PorcupineTTSAndroid TextToSpeechBackendFastAPI (Python) on Hetzner VPSLicense deliveryTelegram Bot + FastAPIUpdatesGitHub Releases APINetworkingHttpURLConnection (no OkHttp)
How to Talk to Dashman
Dashman reads intent from the first word of your phrase. The rest it figures out itself.
Create a reminder — just say the task:
Buy bread tomorrow at 6pm
Call mom on Friday at 10am
Doctor appointment April 15 at 2:30pm
Pay the bill in 3 days
Repeating reminders:
Take pills every day at 8am
Team call every Monday at 10
Gym on weekends at 11
Show reminders:
What's today
Show tomorrow evening
What's this week
Show everything
Delete / Move / Edit:
Delete reminder about bread
Move doctor appointment to Friday at 3pm
Edit the meeting reminder
Installation
Download the latest APK from Releases and install on Android 8.0+.

Allow installation from unknown sources if prompted.

About the Author
I'm Maxim — a construction worker from Brno, Czech Republic. I started coding Android apps on the side with one goal: move into IT.
Dashman started as a Python script and was rewritten from scratch in Kotlin. It's a real product used by real people — not a tutorial project.
I'm currently building my QA portfolio and open to junior QA / junior Android developer roles.
📬 Telegram: @kelevJob
Changelog
Initial public release. Voice reminders, repeat rules, freemium licensing, auto-updates, backup/restore


Что такое Dashman?
Dashman — голосовая напоминалка для Android. Управляется голосом. Просто скажи что нужно сделать — Дашман сам разберётся со временем, датой и повторами. Без форм, без тапов, без шаблонов.
Нажал. Сказал. Забыл. В нужный момент вспомнил.
Возможности

🎙️ Полное голосовое управление — создание, редактирование, перенос, удаление
🕐 Умный парсер времени — понимает живую речь: "через 3 дня", "в следующий понедельник в 10", "по будням в 9 утра"
🔁 Правила повтора — ежедневно, еженедельно, по будням, по выходным, каждые N дней, диапазоны дат
🔍 Голосовой поиск и фильтр — "покажи всё на завтра вечером"
🗣️ Голосовое подтверждение — Дашман проговаривает что сделал
🔑 Система лицензий — freemium с активацией через Telegram-бот
🔄 Автообновления — проверяет GitHub Releases, уведомляет в приложении
💾 Бэкап и восстановление — через Android SAF
🌙 Тёмная тема — всегда, без переключателя

Технологии
СлойТехнологияЯзыкKotlinUIJetpack ComposeАрхитектураMVVMЛокальная БДRoom (SQLite)Распознавание речиAndroid SpeechRecognizerХотвордPicovoice PorcupineTTSAndroid TextToSpeechБэкендFastAPI (Python) на Hetzner VPSЛицензииTelegram Bot + FastAPIОбновленияGitHub Releases APIСетьHttpURLConnection
Установка
Скачай последний APK из Releases и установи на Android 8.0+.

При установке разреши источники из неизвестных источников.

Об авторе
Меня зовут Максим. Строитель из Брно, Чехия. Пишу Android-приложения параллельно со стройкой — с целью перейти в IT.
Dashman начинался как Python-скрипт и был переписан с нуля на Kotlin. Это реальный продукт, которым пользуются реальные люди — не учебный проект.
Сейчас строю портфолио в QA и открыт к предложениям junior QA / junior Android developer.
📬 Telegram: @kelevJob
История версий
ВерсияЧто сделано:Первый публичный релиз. Голосовые напоминания, правила повтора, freemium-лицензирование, автообновления, бэкап/восстановление

Dashman — живой продукт в разработке. Баги есть. Работаю над ними.
