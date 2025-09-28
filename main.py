
from __future__ import annotations

import os, json, re, uuid, random, threading, time
from datetime import datetime, timedelta
from typing import Any, MutableMapping,Optional, cast

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.clock import Clock
from kivy.utils import platform as _plat
from kivy.properties import NumericProperty


try:
    from plyer import notification
except Exception:
    notification = None

DATA_FILE = "reminders.json"
CONFIG_FILE = "config.json"
LOG_FILE = "dashman.log"

DEFAULT_CONFIG = {
    "valid_key": False,
    "license_key":None,
    "server_url": "http://127.0.0.1:8000",
    "app": "Dashman", 
    "device_id": None,
    "ack_phrases": [
        "Слушаю и повинуюсь.",
        "Да, слушаю.",
        "Говори.",
        "Я весь во внимании.",
        "Бля!!!Как ты меня заебал!!!.",
        "Че надо?",
        "Слушаю внимательно."
    ]
}

# ---- PRIORITY ----
PRIORITY_KEYWORDS = {
    "critical": ["срочно", "очень важно", "пиздец", "критично", "ни при каких обстоятельствах", "обязательно"],
    "high": ["важно", "приоритет", "в первую очередь", "максимально быстро", "как можно скорее"],
    "medium": ["желательно", "по возможности", "потом", "как будет время"],
    "low": []
}

RESPONSES = {
    "critical": ["Понял, это очень серьёзно. Зафиксировал.","Срочность красная. Беру под контроль.","Принято. Сверхважная задача."],
    "high": ["Отмечаю как приоритетную.","Понимаю важность. Записал.","Сделаю акцент на этой задаче."],
    "medium": ["Принято без спешки.","Хорошо, учту, но без приоритета.","Записал. Можно сделать по возможности."],
    "low": ["Записал как обычную задачу.","Готово. Без приоритета.","Просто добавил в список."]
}

YES_WORDS = {"да","ага","ок","окей","конечно","разумеется","угу","ясен хуй","естественно"}
NO_WORDS = {"нет","не","не надо","не нужно","отмена","неа"}

RU_WEEKDAYS = ["понедельник","вторник","среда","четверг","пятница","суббота","воскресенье"]
RU_WEEKDAYS_ACC = ["понедельник","вторник","среду","четверг","пятницу","субботу","воскресенье"]

def is_android() -> bool:
    return _plat == "android"

# --- pyjnius / Android classes: real on Android, stubs on PC --
if is_android():
    try:
        # ВАЖНО: импорт до использования autoclass
        from jnius import autoclass, PythonJavaClass, java_method  # type: ignore[reportMissingImports]

        PythonActivity   = autoclass ('org.kivy.android.PythonActivity')
        Intent           = autoclass('android.content.Intent')
        IntentFilter     = autoclass('android.content.IntentFilter')
        Uri              = autoclass('android.net.Uri')
        SpeechRecognizer = autoclass('android.speech.SpeechRecognizer')
        RecognizerIntent = autoclass('android.speech.RecognizerIntent')
        SettingsSecure   = autoclass('android.provider.Settings$Secure')
    except Exception:
        # fallback на заглушки
        PythonActivity = Intent = IntentFilter = Uri = SpeechRecognizer = RecognizerIntent = SettingsSecure = None
else:
    # На ПК — заглушки + аннотации, чтобы Pylance не орал
    from typing import Any, Optional, Type
    
    PythonActivity: Any = None
    Intent: Any = None
    # это класс на Android → в заглушке обозначаем «класс или None»
    IntentFilter: Optional[Type[Any]] = None
    Uri: Any = None
    SpeechRecognizer: Any = None
    RecognizerIntent: Any = None
    SettingsSecure: Any = None

# --- notifications (plyer optional) ---
try:
    from plyer import notification  # type: ignore[reportMissingImports]
except Exception:
    notification = None

def speak(text: Any) -> None:
    msg = str(text)
    try:
        fn = getattr(notification, "notify", None)
        if callable(fn):
            fn(title="Dashman", message=msg)
    except Exception:
        pass
    print(f"[Dashman] {msg}")

# --- helpers: URL, ANDROID_ID, mic permission ---
def open_url_android(url: str) -> bool:
    if not is_android() or not (PythonActivity and Intent and Uri):
        return False
    try:
        intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        PythonActivity.mActivity.startActivity(intent)
        return True
    except Exception as e:
        print(f"[Dashman] open_url_android error: {e}")
        return False

def get_android_id() -> Optional[str]:
    if not is_android() or not (PythonActivity and SettingsSecure):
        return None
    try:
        ctx = PythonActivity.mActivity.getContentResolver()
        aid = SettingsSecure.getString(ctx, SettingsSecure.ANDROID_ID)
        return str(aid) if aid else None
    except Exception as e:
        print(f"[Dashman] get_android_id error: {e}")
        return None

def ensure_mic_permission() -> bool:
    """Request RECORD_AUDIO if needed. Callback is async; we just trigger the request and return True."""
    if not is_android() or not PythonActivity:
        return True
    try:
        from android.permissions import request_permissions, Permission, check_permission  # type: ignore
        if check_permission(Permission.RECORD_AUDIO):
            return True
        request_permissions([Permission.RECORD_AUDIO])
        return True
    except Exception as e:
        print(f"[Dashman] ensure_mic_permission error: {e}")
        return False

# --- ASR start/stop ---
def start_listening_android(lang_code: str = "ru-RU") -> bool:
    """Start Google ASR. Attach your RecognitionListener if you need callbacks."""
    if not is_android() or not (PythonActivity and SpeechRecognizer and RecognizerIntent and Intent):
        return False
    try:
        sr = SpeechRecognizer.createSpeechRecognizer(PythonActivity.mActivity)  # type: ignore[attr-defined]
        # NOTE: store sr somewhere (e.g., self._sr) if you need to stop/destroy later
        intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)  # type: ignore[attr-defined]
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang_code)  # type: ignore[attr-defined]
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, True)  # type: ignore[attr-defined]
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)  # type: ignore[attr-defined]
        sr.startListening(intent)
        return True
    except Exception as e:
        print(f"[Dashman] start_listening_android error: {e}")
        return False

def stop_listening_android(sr_obj: Any) -> None:
    try:
        if is_android() and sr_obj:
            sr_obj.stopListening()
            try:
                sr_obj.destroy()
            except Exception:
                pass
    except Exception as e:
        print(f"[Dashman] stop_listening_android error: {e}")

# --- BroadcastReceiver (hotword) with outer & correct __javaclass__ ---
# ---- Hotword receiver (единая точка объявления) ----
def _hotword_log(msg: str) -> None:
    try:
        # если глобальный log уже есть — используем его
        log(msg)  # type: ignore[name-defined]
    except Exception:
        # иначе — безопасный вывод
        print(f"[Dashman] {msg}")
        
from typing import Type, Any, cast as _cast

class _HotwordReceiverBase:
    def __init__(self, outer: Any | None = None) -> None:
        pass

# По умолчанию (на ПК) — Stub-класс
class _HotwordReceiverStub(_HotwordReceiverBase):
    def __init__(self, outer: Any | None = None) -> None:
        super().__init__(outer)

# Важно: имя HotwordReceiver сразу указывает на КЛАСС (не None)
HotwordReceiver: Type[_HotwordReceiverBase] = _HotwordReceiverStub

# На Android переопределяем реальной реализацией
if is_android() and PythonJavaClass and java_method:
    try:
        class _HotwordReceiverImpl(PythonJavaClass):  # type: ignore[misc]
            javainterfaces = ['android/content/BroadcastReceiver']
            javacontext = 'app'

            def __init__(self, outer: Any | None = None) -> None:
                super().init()
                self._outer = outer

            @java_method('(Landroid/content/Context;Landroid/content/Intent;)V')
            def onReceive(self, context, intent) -> None:
                # ТВОЁ тело onReceive (как было)
                try:
                    if self._outer:
                        self._outer.on_hotword_intent(intent)
                except Exception as e:
                    _hotword_log(f"HotwordReceiverImpl onReceive error: {e}")

        HotwordReceiver = _cast(Type[_HotwordReceiverBase], _HotwordReceiverImpl)
    except Exception as e:
        _hotword_log(f"HotwordReceiver fallback to stub: {e}")
        # оставляем HotwordReceiver = _HotwordReceiverStub

def register_hotword_receiver(outer, action_name: str = "com.kelev.dashman.HOTWORD") -> bool:
    if not is_android() or not (PythonActivity and IntentFilter and HotwordReceiver):
        return False
    try:
        global _hotword_receiver
        _hotword_receiver = HotwordReceiver(outer)
        flt = IntentFilter()
        flt.addAction(action_name)
        act = getattr(PythonActivity, "mActivity", None)
        if act is not None:
            act.registerReceiver(_hotword_receiver, flt)
            return True
    except Exception as e:
        print(f"[Dashman] register_hotword_receiver error: {e}")
    return False

def unregister_hotword_receiver() -> None:
    if not is_android() or not PythonActivity:
        return
    try:
        global _hotword_receiver
        if _hotword_receiver:
            act = getattr(PythonActivity, "mActivity", None)
            if act is not None:
                act.unregisterReceiver(_hotword_receiver)
            _hotword_receiver = None
    except Exception as e:
        print(f"[Dashman] unregister_hotword_receiver error: {e}")

# --- Foreground service via AndroidService ---
def start_hotword_service(svc_name: str = "hotword") -> bool:
    if not is_android():
        return False
    try:
        from android import AndroidService  # type: ignore[reportMissingImports]
        srv = AndroidService('Dashman Hotword', 'Скажите «Эй, Дашман»')
        if srv is not None:
            # svc_name must match buildozer.spec: services = hotword:service_hotword_fixed.py
            srv.start(svc_name)
            return True
    except Exception as e:
        print(f"[Dashman] start_hotword_service error: {e}")
    return False

def stop_hotword_service(svc_name: str = "hotword") -> None:
    try:
        from android import AndroidService  # type: ignore[reportMissingImports]
        srv = AndroidService('Dashman Hotword', '')
        if srv is not None:
            srv.stop()
    except Exception as e:
        print(f"[Dashman] stop_hotword_service error: {e}")

# fallback hook
def on_hotword_detected():
    speak("Хотворд распознан.")
# ===================== /DASHMAN HELPERS — INLINE SINGLE-FILE BLOCK =====================


# ---- UTILS ----
def log(msg):
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(f"[{datetime.now().isoformat()}] {msg}\n")
    except Exception:
        pass

def load_json(path, default):
    try:
        if os.path.exists(path):
            with open(path,"r",encoding="utf-8") as f:
                return json.load(f)
    except Exception as e:
        log(f"load_json error: {e}")
    return default

def save_json(path, data):
    try:
        with open(path,"w",encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception as e:
        log(f"save_json error: {e}")

def get_device_id():
    try:
        if is_android() and PythonActivity is not None and SettingsSecure is not None:
            ctx = getattr(PythonActivity, "mActivity", None)
            if ctx is not None:
                cr = ctx.getContentResolver()
                aid = SettingsSecure.getString(cr, SettingsSecure.ANDROID_ID)
                if aid:
                    return f"android-{aid}"
    except Exception:
        pass
    return f"uuid-{uuid.uuid4()}"

def ack_phrase(cfg):
    return random.choice((cfg or {}).get("ack_phrases") or DEFAULT_CONFIG["ack_phrases"])

def analyze_priority(text: str):
    t = (text or "").lower()
    for level in ["critical","high","medium","low"]:
        if any(kw in t for kw in PRIORITY_KEYWORDS[level]):
            return level
    return "low"

def choose_priority_response(level: str):
    return random.choice(RESPONSES.get(level) or RESPONSES["low"])

# ---- ONLINE ACTIVATION ----
def validate_key_online(server_base, device_id, key_text, app_name="Dashman", timeout=8):
    if not server_base or not key_text:
        return False, "Пустой адрес сервера или ключ."
    try:
        import urllib.request, json as _json
        url = server_base.rstrip("/") + "/validate"
        payload = _json.dumps({"app": app_name, "device_id": device_id, "key": key_text}).encode("utf-8")
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"}, method="POST")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = _json.loads(resp.read().decode("utf-8","ignore"))
            ok = bool(data.get("ok"))
            return ok, (data.get("message") or ("Ключ принят." if ok else "Ключ отклонён."))
    except Exception as e:
        return False, f"Сервер недоступен: {e}"

# ---- DATE/TIME PARSING (rich) ----
def parse_weekday_to_date(target, now=None):
    now = now or datetime.now()
    names = {name:i for i,name in enumerate(RU_WEEKDAYS)}
    names.update({acc:i for i,acc in enumerate(RU_WEEKDAYS_ACC)})
    t = target.lower()
    if t not in names: return None
    wd = names[t]
    days_ahead = (wd - now.weekday()) % 7
    if days_ahead == 0: days_ahead = 7
    return (now + timedelta(days=days_ahead)).date()

def parse_time_token(text):
    m = re.search(r"\b(\d{1,2})[:\.](\d{2})\b", text)
    if m:
        h = int(m.group(1)); mi = int(m.group(2))
        return max(0,min(23,h)), max(0,min(59,mi))
    m = re.search(r"(?:в|к)\s*(\d{1,2})(?:\s*час[аов]?)?\b", text)
    if m:
        h = int(m.group(1)); return max(0,min(23,h)), 0
    if "утр" in text: return 8,0
    if "дн" in text: return 12,0
    if "веч" in text: return 20,0
    return None

def parse_relative(text, now=None):
    now = now or datetime.now()
    m = re.search(r"через\s+(\d+)\s*(минут[уы]?|час[аов]?|д(ень|ня|ней)|недел[яюи])", text)
    if not m: return None
    n = int(m.group(1)); unit = m.group(2)
    if unit.startswith("мин"): return now + timedelta(minutes=n)
    if unit.startswith("час"): return now + timedelta(hours=n)
    if unit.startswith("д"): return now + timedelta(days=n)
    if unit.startswith("нед"): return now + timedelta(weeks=n)
    return None

def parse_time_and_repeat_full(text, now=None):
    """Возвращает (dt, repeat, needs_time_clarification)"""
    now = now or datetime.now()
    t = (text or "").lower()

    # repeats
    repeat = None
    if "каждый день" in t or "ежедневно" in t:
        repeat = {"kind":"daily"}
    elif "по будням" in t:
        repeat = {"kind":"weekdays"}
    elif "по выходным" in t:
        repeat = {"kind":"weekend"}
    else:
        m = re.search(r"каждые?\s+(\d+)\s*(минут[уы]?|час[аов]?|д(ень|ня|ней)|недел[яюи])", t)
        if m:
            repeat = {"kind":"every","n":int(m.group(1)),"unit":m.group(2)}

    # relative
    dt = parse_relative(t, now=now)
    if dt: return dt, repeat, False

    # explicit time
    tm = parse_time_token(t)

    # date words
    base = None
    if "послезавтра" in t: base = now + timedelta(days=2)
    elif "завтра" in t: base = now + timedelta(days=1)
    elif "сегодня" in t: base = now

    # weekday
    if base is None:
        for name in set(RU_WEEKDAYS+RU_WEEKDAYS_ACC):
            if name in t:
                d = parse_weekday_to_date(name, now=now)
                if d: base = datetime.combine(d, datetime.min.time())
                break

    if base is not None:
        if tm:
            h,mi = tm; dt = base.replace(hour=h,minute=mi,second=0,microsecond=0)
            if dt <= now and "сегодня" in t: dt = dt + timedelta(days=1)
            return dt, repeat, False
        else:
            dt = base.replace(hour=9, minute=0, second=0, microsecond=0)
            return dt, repeat, True

    # time only → nearest slot
    if tm:
        h,mi = tm
        dt = now.replace(hour=h,minute=mi,second=0,microsecond=0)
        if dt <= now: dt += timedelta(days=1)
        return dt, repeat, False

    # fallback
    return now + timedelta(minutes=10), repeat, False

def parse_time_only(text, default_hour=9):
    tm = parse_time_token((text or "").lower())
    if tm: return tm
    return default_hour, 0

class VoiceRecognizer:
    # Обёртка вокруг android.speech.SpeechRecognizer для ОДНОГО распознавания.
    # Вызывает on_result(text|None) один раз и сам себя чистит.
    def __init__(self):
        self._enabled = is_android()
        self._sr = None
        self._listener = None
        self._on_result_cb = None
        self._active = False
        if not self._enabled:
            return

        self.SpeechRecognizer = autoclass('android.speech.SpeechRecognizer')
        self.RecognizerIntent = autoclass('android.speech.RecognizerIntent')
        self.Intent          = autoclass('android.content.Intent')
        self.PythonActivity  = autoclass('org.kivy.android.PythonActivity')

        outer = self
        class _Listener(PythonJavaClass):
            __javainterfaces__ = ['android/speech/RecognitionListener']
            __javacontext__    = 'app'

            def __init__(self):
                super().__init__()

            @java_method('(Landroid/os/Bundle;)V')
            def onReadyForSpeech(self, params): pass
            @java_method('()V')
            def onBeginningOfSpeech(self): pass
            @java_method('(F)V')
            def onRmsChanged(self, rmsdB): pass
            @java_method('([B)V')
            def onBufferReceived(self, buffer): pass
            @java_method('()V')
            def onEndOfSpeech(self): pass

            @java_method('(Landroid/os/Bundle;)V')
            def onResults(self, results):
                try:
                    data = results.getStringArrayList(outer.SpeechRecognizer.RESULTS_RECOGNITION)
                    text = (data.get(0) or "") if data else ""
                    outer._finish(True, text)
                except Exception as e:
                    log(f"[ASR] onResults error: {e}")
                    outer._finish(False, "")

            @java_method('(Landroid/os/Bundle;)V')
            def onPartialResults(self, partialResults): pass

            @java_method('(I)V')
            def onError(self, error):
                log(f"[ASR] error code: {error}")
                outer._finish(False, "")

            @java_method('(ILandroid/os/Bundle;)V')
            def onEvent(self, eventType, params): pass

        self._ListenerCls = _Listener

    def start(self, on_result):
        #"""Запускает одно распознавание. Потом вызывает on_result(text|None)."""
        if not self._enabled:
            on_result(None); return
        if self._active:
            return
        try:
            self._on_result_cb = on_result
            self._sr = self.SpeechRecognizer.createSpeechRecognizer(self.PythonActivity.mActivity)  # type: ignore[attr-defined]
            self._listener = self._ListenerCls()
            self._sr.setRecognitionListener(self._listener)

            intent = self.Intent(self.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)  # type: ignore[attr-defined]
            intent.putExtra(self.RecognizerIntent.EXTRA_LANGUAGE_MODEL, self.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)  # type: ignore[attr-defined]
            intent.putExtra(self.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")  # type: ignore[attr-defined]
            intent.putExtra(self.RecognizerIntent.EXTRA_MAX_RESULTS, 1)  # type: ignore[attr-defined]
            intent.putExtra(self.RecognizerIntent.EXTRA_PARTIAL_RESULTS, False)  # type: ignore[attr-defined]
            intent.putExtra(self.RecognizerIntent.EXTRA_CALLING_PACKAGE, self.PythonActivity.mActivity.getPackageName())  # type: ignore[attr-defined]

            self._active = True
            self._sr.startListening(intent)
        except Exception as e:
            log(f"[ASR] start error: {e}")
            self._finish(False, "")

    def _finish(self, success: bool, text: str):
        # чистим
        try:
            if self._sr:
                try: self._sr.cancel()
                except: pass
                try: self._sr.destroy()
                except: pass
        finally:
            self._sr = None
            self._listener = None
            self._active = False
            
        cb = self._on_result_cb
        self._on_result_cb = None
        if cb:
            # возвращаемся на UI-поток Kivy
            from kivy.clock import Clock as _Clock_for_asr
            _Clock_for_asr.schedule_once(lambda dt: cb(text if (success and text) else None), 0)

# ---- APP ----
class ReminderApp(App):
    pulse_r = NumericProperty(0.0)
    pulse_a = NumericProperty(0.0)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.reminders = load_json(DATA_FILE, [])
        cfg = load_json(CONFIG_FILE, DEFAULT_CONFIG.copy())
        for k,v in DEFAULT_CONFIG.items():
            if k not in cfg: cfg[k] = v
        self.config_data = cfg
        if not self.config_data.get("device_id"):
            self.config_data["device_id"] = get_device_id()
            save_json(CONFIG_FILE, self.config_data)

        self.last_reminder = None
        self._schedule_thread = None
        self._schedule_stop = False
        self._listening = False
        self._hotword_receiver = None
        self.dialog = None
        self.pending_delete = None
        self._pulse_ev = None

    def build(self):
        self._asr_once = VoiceRecognizer()
        self._listening = False
        root = BoxLayout(); self.root = root
        Clock.schedule_once(lambda dt: speak("Готов к работе." if self.is_activated() else "Пробую получить ключ..."), 0.2)
        return root
    
    def on_start(self):
        # """
        # Стартуем всё Android-специфичное:
        # 1) просим разрешение на микрофон;
        # 2) поднимаем foreground-сервис хотворда;
        # 3) регистрируем BroadcastReceiver для события HOTWORD.
        # На ПК (не Android) — просто выходим.
        # """
        if not self.is_activated():
            if self.try_autoregister():
                log("autoregister ok")
            else:
                log("autoregister failed — app locked")
                return
            
        if not is_android():
            log("on_start: non-Android environment, skip Android init")
            return

        # 1) Runtime-разрешение на микрофон
        try:
            from android.permissions import request_permissions, Permission  # type: ignore[reportMissingImports]
            request_permissions([Permission.RECORD_AUDIO])
        except Exception as e:
            log(f"permission request error: {e}")

        # 2) Запуск foreground-сервиса хотворда
        self._hotword_srv = None
        try:
            from android import AndroidService  # type: ignore[reportMissingImports]
            srv = AndroidService('dashman Hotword', 'Скажите «Эй, Дашаман»')
            if srv is not None:
                try:
                    # 'hotword' — это ключ слева из buildozer.spec: services = hotword:service_hotword_fixed.py
                    srv.start('hotword')
                except Exception as e:
                    log(f"AndroidService.start error: {e}")
            else:
                log("AndroidService returned None (desktop?)")
            self._hotword_srv = srv
        except Exception as e:
            log(f"start hotword service error: {e}")
            self._hotword_srv = None

        # 3) Регистрация ресивера хотворда
        try:
            IF_cls = IntentFilter
            if IF_cls is None:            # на ПК заглушка может быть None — выходим
                return
            f = IF_cls()
            f.addAction("com.kelev.dashman.HOTWORD")

            # Инстанс ресивера (теперь это точно класс: реальный на Android, Stub на ПК)
            self._hotword_receiver = HotwordReceiver(self)

            # Берём текущее Activity и регистрируем ресивер
            act = getattr(PythonActivity, "mActivity", None)
            if act is not None:
                act.registerReceiver(self._hotword_receiver, f)
            else:
                log("registerReceiver skipped: no mActivity")
        except Exception as e:
            log(f"registerReceiver error: {e}")
            self._hotword_receiver = None
    
        # 4) старт фонового планировщика
        try:
            if not self._schedule_thread or not self._schedule_thread.is_alive():
                self._schedule_stop = False
                self._schedule_thread = threading.Thread(target=self._run_scheduler, daemon=True)
                self._schedule_thread.start()
                log("scheduler: started")
        except Exception as e:
            log(f"scheduler start error: {e}")
    
    def on_stop(self):
    # 1) снять ресивер
        if is_android() and getattr(self, "_hotword_receiver", None):
            try:
                act = getattr(PythonActivity, "mActivity", None)
                if act is not None:
                    act.unregisterReceiver(self._hotword_receiver)   # <-- НЕ mActivity_unregisterReceiver
                else:
                    log("unregisterReceiver skipped: no mActivity")
            except Exception as e:
                log(f"unregisterReceiver error: {e}")
            finally:
                self._hotword_receiver = None

        # 2) остановить сервис хотворда
        if is_android() and getattr(self, "_hotword_srv", None):
            try:
                if self._hotword_srv is not None:  # <-- защита от None
                    self._hotword_srv.stop()
            except Exception as e:
                log(f"hotword service stop error: {e}")
            finally:
                self._hotword_srv = None

        # 3) остановить планировщик
        try:
            self._schedule_stop = True
            th = getattr(self, "_schedule_thread", None)
            if th and th.is_alive():
                log("scheduler: stop requested")
        except Exception as e:
            log(f"scheduler stop error: {e}")

    def try_autoregister(self) -> bool:
        try:
            import urllib.request, json as _json
            url = (self.config_data.get("server_url") or "").rstrip("/") + "/autoregister"
            payload = _json.dumps({
                "app": self.config_data.get("app") or "Dashman",
                "device_id": self.config_data.get("device_id")
            }).encode("utf-8")
            req = urllib.request.Request(url, data=payload, headers={"Content-Type":"application/json"}, method="POST")
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = _json.loads(resp.read().decode("utf-8","ignore"))
                if data.get("ok") and data.get("key"):
                    self.config_data["license_key"] = data["key"]
                    self.config_data["valid_key"] = True
                    save_json(CONFIG_FILE, self.config_data)
                    return True
        except Exception as e:
            log(f"autoregister fail: {e}")
        return False

    def is_activated(self) -> bool:
        return bool(self.config_data.get("valid_key"))

    # ---- Pulse animation ----
    def _pulse_step(self, dt):
        self.pulse_r += 3.5
        self.pulse_a = max(0.0, 0.35 - (self.pulse_r / 120.0))
        if self.pulse_r >= 120:
            self.pulse_r = 0.0; self.pulse_a = 0.35

    def _start_listen_anim(self):
        self.pulse_r = 0.0; self.pulse_a = 0.35
        if not self._pulse_ev:
            self._pulse_ev = Clock.schedule_interval(self._pulse_step, 1/30.0)

    def _stop_listen_anim(self):
        if self._pulse_ev:
            self._pulse_ev.cancel(); self._pulse_ev = None
        self.pulse_r = 0.0; self.pulse_a = 0.0

    # ---- UI glue ----
    def set_status(self, txt):
        try:
            if hasattr(self, "status_bar"):
                self.status_bar.text = txt
        except Exception:
            pass

    # ---- Activation ----
    def validate_key(self, key_text):
        key_text = (key_text or "").strip()
        if not key_text:
            speak("Ключ пустой.")
            return

        ok, msg = validate_key_online(
            self.config_data.get("server_url"),
            self.config_data.get("device_id"),
            key_text,
            self.config_data.get("app") or "Dashman"   # <-- вот это важно
        )

        if ok:
            self.config_data["valid_key"] = True
            save_json(CONFIG_FILE, self.config_data)
            speak("Ключ принят. Добро пожаловать.")
            try:
                if hasattr(self, "sm"):
                    self.sm.current = "main"
            except Exception:
                pass
        else:
            speak(msg or "Неверный ключ.")

    # ---- Mic ----
    def on_mic_tap(self, *a): self.listen_voice()

    # ---- Listening ----
    def listen_voice(self, *a):
        if getattr(self, "_listening", False):
            return
        self._listening = True

        try:
            if hasattr(self, "_start_listen_anim"): self._start_listen_anim()
        except: pass
        try:
            self.set_status("Слушаю...")
        except: pass
        try:
            speak(ack_phrase(self.config_data) if hasattr(self, "config_data") else "Слушаю команду…")
        except:
            speak("Слушаю команду…")

    # ВНУТРЕННИЙ колбэк результата — ВНУТРИ listen_voice
        def _on_result(text):
            try:
                if hasattr(self, "_stop_listen_anim"): self._stop_listen_anim()
            except: pass
            self._listening = False

            if not text:
                speak("Не расслышал. Повтори после «Эй, Дашаман».")
                return

            try:
                self._on_recognized(text)
            except Exception as e:
                log(f"[ASR] _on_recognized error: {e}")
                speak("Ошибка обработки команды.")

    # А вот ЭТО — СНАРУЖИ _on_result (правильный отступ!)
        if not hasattr(self, "_asr_once"):
            self._asr_once = VoiceRecognizer()
        self._asr_once.start(_on_result)
    
    def _on_recognized(self, text):
        self._listening = False
        self._stop_listen_anim()
        self.set_status("Готов.")
        self.process_voice_command(text)

    # ---- Upcoming + UI refresh ----
    def get_upcoming(self, limit=10):
        now = datetime.now(); items = []
        for r in self.reminders:
            if self._is_done(r): continue
            try:
                when = datetime.fromisoformat(r["when"])
                if when >= now: items.append((when, r.get("text","без названия"), r.get("priority","low")))
            except Exception: pass
        items.sort(key=lambda x: x[0])
        return items[:limit]

    def refresh_ui(self):
        box = getattr(self, "reminders_box", None)
        if not box: return
        box.clear_widgets()
        try:
            from kivy.uix.boxlayout import BoxLayout
            from kivy.uix.label import Label
            from kivy.metrics import dp
        except Exception:
            return
        up = self.get_upcoming(limit=20)
        if not up:
            box.add_widget(Label(text="Пусто. Скажи: “Напомни … завтра в 19:30”",
                                 color=(0.7,0.7,0.7,1), size_hint_y=None, height=dp(22)))
            return
        for when, text, prio in up:
            row = BoxLayout(orientation="horizontal", size_hint_y=None, height=dp(44), padding=(dp(8),0))
            lbl = Label(text=text, color=(1,1,1,1), halign="left", valign="middle")
            lbl.bind(size=lambda *_: setattr(lbl, "text_size", lbl.size))#type:ignore[attr-defined]
            tlabel = Label(text=when.strftime("%d.%m %H:%M"), color=(0.85,0.85,0.85,1), size_hint_x=None, width=dp(96))
            tag = {"critical":"🔥","high":"⚠️","medium":"•","low":""}.get(prio, "")
            plabel = Label(text=tag, color=(1,1,1,1), size_hint_x=None, width=dp(24))
            row.add_widget(plabel); row.add_widget(lbl); row.add_widget(tlabel)
            box.add_widget(row)

    # ---- Commands & dialogs ----
    def process_voice_command(self, text):
        t = (text or "").lower().strip()
        log(f"CMD: {t}")
        if not t:
            speak("Не расслышал."); return

        # follow-up: ask_time
        
        if isinstance(self.dialog, dict) and self.dialog.get("type") == "ask_time":
            t_norm = (t or "").strip().lower()
            h, mi = parse_time_only(t_norm)

            # dlg — гарантированно "изменяемый маппинг" строк -> что угодно
            dlg: MutableMapping[str, Any] = cast(MutableMapping[str, Any], self.dialog)

            # base_reminder — должен быть dict; если нет, создаём
            base_raw = dlg.get("base_reminder")
            if isinstance(base_raw, dict):
                base: MutableMapping[str, Any] = cast(MutableMapping[str, Any], base_raw)
            else:
                base = {}
                # <-- вот та самая проблемная строка: типизатору явно говорим, что так можно
                dlg["base_reminder"] = cast(Any, base)  # успокаиваем Pylance

            try:
                # берём опорную дату из base["when"] (если есть), иначе сегодня
                base_when_str = base.get("when")
                if isinstance(base_when_str, str):
                    base_when = datetime.fromisoformat(base_when_str)
                else:
                    base_when = datetime.now()

                when = base_when.replace(hour=h, minute=mi, second=0, microsecond=0)
                base["when"] = when.isoformat()

                # твоя логика сохранения
                save_json(DATA_FILE, self.reminders)
                speak(f"Принял. В {h:02d}:{mi:02d}.")
            except Exception:
                speak("Не смог понять время.")

            self.dialog = None
            self.refresh_ui()
            return

        # follow-up: prealert
        if isinstance(self.dialog, dict) and self.dialog.get("type") == "prealert":
            if t in YES_WORDS:
                dlg: MutableMapping[str, Any] = cast(MutableMapping[str, Any], self.dialog)

                # 1) минутЫ предупреждения (строго и безопасно)
                mins_val = dlg.get("minutes")
                try:
                    mins = int(mins_val) if mins_val is not None else 60
                except Exception:
                    mins = 60

                # 2) base_reminder берём отдельно и проверяем тип
                base_raw = dlg.get("base_reminder")
                if not (isinstance(base_raw, dict) and isinstance(base_raw.get("when"), str)):
                    speak("Не нашёл время для предварительного напоминания.")
                    self.dialog = None
                    self.refresh_ui()
                    return

                base: MutableMapping[str, Any] = cast(MutableMapping[str, Any], base_raw)

                # 3) создаём предварительное напоминание
                try:
                    when = datetime.fromisoformat(base["when"])  # type: ignore[index]
                    pre = {
                        "text": f"Предупреждение: {base.get('text', '')}",
                        "when": (when - timedelta(minutes=mins)).isoformat(),
                        "repeat": None,
                        "status": "active",
                        "priority": base.get("priority", "high"),
                    }
                    self.reminders.append(pre)
                    save_json(DATA_FILE, self.reminders)
                    speak("Сделаю предварительное напоминание.")
                except Exception:
                    speak("Не смог поставить предварительное напоминание.")
            else:
                speak("Ок, без предварительного напоминания.")

            self.dialog = None
            self.refresh_ui()
            return

        # follow-up: confirm_delete
        if isinstance(self.dialog, dict) and self.dialog.get("type") == "confirm_delete":
            if t in YES_WORDS:
                # 1) достаём items безопасно: если pending_delete пустой/не dict — берём []
                pd = self.pending_delete if isinstance(self.pending_delete, dict) else {}
                items = pd.get("items")
                if not isinstance(items, (list, tuple)):
                    items = []

                # 2) считаем id выбранных объектов и фильтруем текущие напоминания
                before = len(self.reminders)
                ids = {id(x) for x in items}  # теперь items точно итерируем
                self.reminders = [r for r in self.reminders if id(r) not in ids]

                # 3) сохраняем и озвучиваем результат
                save_json(DATA_FILE, self.reminders)
                removed = before - len(self.reminders)
                speak(f"Удалил {removed}.") if removed else speak("Удалять нечего.")
                self.refresh_ui()
            else:
                speak("Отменил удаление.")

            # 4) чистим диалоговое состояние
            self.dialog = None
            self.pending_delete = None
            return

        # system commands
        if "очист" in t and ("выполн" in t or "заверш" in t):
            self.offer_cleanup(); return
        if "выполн" in t or "готово" in t or "сделано" in t:
            self.mark_last_as_done(); return
        if t.startswith("удали") or "удалить" in t:
            key = t.replace("удали","").replace("удалить","").replace("напоминание","").strip()
            if key: self.delete_reminders_by_voice_enhanced(key); return
        if "отлож" in t or "перенес" in t or "попозже" in t:
            self.postpone_last_by_text(t); return
        if t.startswith("что у меня") or t.startswith("какие напоминания"):
            self.read_upcoming(); return

        # create
        self.create_reminder_from_text(t)

    # ---- Create with priority & dialogs ----
    def create_reminder_from_text(self, text):
        content = text
        for kw in [" в ", "через ", "завтра", "послезавтра", "сегодня"] + RU_WEEKDAYS + RU_WEEKDAYS_ACC + ["каждый","по "]:
            if kw in text:
                content = text.split(kw)[0].replace("напомни","").replace("запомни","").strip()
                break
        level = analyze_priority(text)
        when, repeat, ask_time_flag = parse_time_and_repeat_full(text)
        item = {"text": content or "без названия", "when": when.isoformat(), "repeat": repeat, "status": "active", "priority": level}
        self.reminders.append(item); save_json(DATA_FILE, self.reminders); self.last_reminder = item
        speak(f"{choose_priority_response(level)} Напомню: {item['text']} — {when.strftime('%d.%m %H:%M')}.")
        if ask_time_flag:
            self.dialog = {"type":"ask_time", "base_reminder": item}; speak("Во сколько напомнить? Назови время.")
        elif level in ("critical","high"):
            self.dialog = {"type":"prealert", "minutes":60, "base_reminder": item}; speak("Поставить предупреждение за час до этого? Скажи да или нет.")
        self.refresh_ui()

    # ---- Postpone ----
    def postpone_last_by_text(self, text):
        if not self.last_reminder: speak("Нет последнего напоминания."); return
        when = datetime.fromisoformat(self.last_reminder["when"])
        if "мин" in text: when += timedelta(minutes=10)
        elif "час" in text: when += timedelta(hours=1)
        elif "завтра" in text:
            when = (datetime.now() + timedelta(days=1)).replace(hour=when.hour, minute=when.minute, second=0, microsecond=0)
        elif any(w in text for w in RU_WEEKDAYS+RU_WEEKDAYS_ACC):
            for wd in set(RU_WEEKDAYS+RU_WEEKDAYS_ACC):
                if wd in text:
                    d = parse_weekday_to_date(wd)
                    if d: when = datetime.combine(d, when.time()); break
        else:
            when += timedelta(minutes=15)
        self.last_reminder["when"] = when.isoformat(); save_json(DATA_FILE, self.reminders); speak("Перенёс.")
        self.refresh_ui()

    # ---- Delete ----
    def delete_reminders_by_voice_enhanced(self, key):
        key = (key or "").strip().lower()
        if not key: speak("Ключевое слово не понял."); return
        if "выполнен" in key or "заверш" in key:
            items = [r for r in self.reminders if self._is_done(r)]
        elif "все" in key:
            items = list(self.reminders)
        else:
            items = [r for r in self.reminders if key in r.get("text","").lower()]
        if not items:
            speak("Ничего похожего не нашёл."); return
        self.pending_delete = {"mode":"by_key","items": items}; self.dialog = {"type":"confirm_delete"}
        speak(f"Нашёл {len(items)}. Удалить? Скажи да или нет.")

    # ---- Done helpers ----
    def _is_done(self, r):
        if isinstance(r, dict):
            if r.get("status") == "done": return True
            if r.get("completed") is True: return True
        return False
    
    def _mark_done(self, r):
        if isinstance(r, dict):
            r["status"]="done"; r.pop("completed", None)
        return r

    def mark_last_as_done(self):
        active = [r for r in self.reminders if not self._is_done(r)]
        if not active: speak("Нет активных задач."); return
        last = active[-1]; self._mark_done(last); save_json(DATA_FILE, self.reminders)
        speak(f"Пометил как выполненное: {last.get('text','без названия')}.")
        self.refresh_ui()

    # ---- Cleanup ----
    def offer_cleanup(self):
        before = len(self.reminders)
        self.reminders = [r for r in self.reminders if not self._is_done(r)]
        removed = before - len(self.reminders); save_json(DATA_FILE, self.reminders)
        speak(f"Очистил {removed} завершённых.") if removed else speak("Нет завершённых.")
        self.refresh_ui()

    # ---- Reporting ----
    def read_upcoming(self):
        now = datetime.now(); upcoming = []
        for r in self.reminders:
            if not self._is_done(r):
                try:
                    when = datetime.fromisoformat(r["when"])
                    if when >= now: upcoming.append((when, r["text"], r.get("priority","low")))
                except Exception: pass
        upcoming.sort(key=lambda x: x[0])
        if not upcoming: speak("Ближайших нет."); return
        head = upcoming[:5]
        msg = "; ".join([f"{txt} — {dt.strftime('%d.%m %H:%M')} ({prio})" for dt, txt, prio in head])
        speak(msg)

    # ---- Scheduler + weekly clean ----
    def _run_scheduler(self):
        next_cleanup = None
        while not getattr(self, "_schedule_stop", False):
            try:
                self.check_reminders()
                now = datetime.now()
                if next_cleanup is None or now >= next_cleanup:
                    days_ahead = (6 - now.weekday()) % 7
                    date = (now + timedelta(days=days_ahead)).replace(hour=19, minute=0, second=0, microsecond=0)
                    if date <= now: date += timedelta(days=7)
                    next_cleanup = date
                if next_cleanup and abs((next_cleanup - now).total_seconds()) < 30:
                    self.offer_cleanup(); next_cleanup = None
            except Exception as e:
                log(f"scheduler error: {e}")
            time.sleep(30)

    def check_reminders(self):
        now = datetime.now(); changed = False
        for r in self.reminders:
            if self._is_done(r): continue
            try: when = datetime.fromisoformat(r["when"])
            except Exception: continue
            if when <= now:
                prefix = "Важно: " if r.get("priority") in ("high","critical") else ""
                speak(f"{prefix}Напоминание: {r.get('text','без названия')}")
                rep = r.get("repeat")
                if rep and isinstance(rep, dict):
                    kind = rep.get("kind")
                    if kind == "daily":
                        r["when"] = (when + timedelta(days=1)).isoformat()
                    elif kind == "weekdays":
                        nxt = when + timedelta(days=1)
                        while nxt.weekday() > 4: nxt += timedelta(days=1)
                        r["when"] = nxt.isoformat()
                    elif kind == "weekend":
                        nxt = when + timedelta(days=1)
                        while nxt.weekday() < 5: nxt += timedelta(days=1)
                        r["when"] = nxt.isoformat()
                    elif kind == "every":
                        n = int(rep.get("n",1)); unit = str(rep.get("unit","день"))
                        if unit.startswith("мин"): r["when"] = (when + timedelta(minutes=n)).isoformat()
                        elif unit.startswith("час"): r["when"] = (when + timedelta(hours=n)).isoformat()
                        elif unit.startswith("д"): r["when"] = (when + timedelta(days=n)).isoformat()
                        elif unit.startswith("нед"): r["when"] = (when + timedelta(weeks=n)).isoformat()
                        else: r["when"] = (when + timedelta(days=n)).isoformat()
                else:
                    self._mark_done(r)
                changed = True
        if changed: save_json(DATA_FILE, self.reminders); self.refresh_ui()

if __name__ == "__main__":
    ReminderApp().run()