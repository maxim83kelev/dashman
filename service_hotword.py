
# -*- coding: utf-8 -*-
# Android Foreground Service: hotword "Эй, Дашман" (fixed)
import time, threading
from kivy.utils import platform
if platform != "android":
    raise SystemExit("Этот сервис работает только на Android.")

from jnius import autoclass, PythonJavaClass, java_method, cast

# --- Java classes ---
Context = autoclass('android.content.Context')
PythonService = autoclass('org.kivy.android.PythonService')
NotificationManager = autoclass('android.app.NotificationManager')
NotificationChannel = autoclass('android.app.NotificationChannel')
NotificationCompatBuilder = autoclass('androidx.core.app.NotificationCompat$Builder')
PendingIntent = autoclass('android.app.PendingIntent')
Intent = autoclass('android.content.Intent')
IntentFilter = autoclass('android.content.IntentFilter')
Build = autoclass('android.os.Build')
SpeechRecognizer = autoclass('android.speech.SpeechRecognizer')
RecognizerIntent = autoclass('android.speech.RecognizerIntent')

# --- Globals ---
_service = None
_recognizer = None
_listener = None
_intent = None
_pause_receiver = None

_last_hit = 0.0
_partial_buf = []

# --- Foreground notification ---
def start_foreground(service):
    context = service
    channel_id = "dashman_hotword"
    nm = cast(NotificationManager, context.getSystemService(Context.NOTIFICATION_SERVICE))
    if Build.VERSION.SDK_INT >= 26:
        channel = NotificationChannel(channel_id, "dashman Hotword", NotificationManager.IMPORTANCE_MIN)
        nm.createNotificationChannel(channel)
    app_intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName())
    app_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
    pending = PendingIntent.getActivity(context, 0, app_intent, PendingIntent.FLAG_IMMUTABLE)
    builder = NotificationCompatBuilder(context, channel_id)
    builder.setContentTitle("dashman слушает")
    builder.setContentText("Скажите: «Эй, Дашман»")
    builder.setSmallIcon(context.getApplicationInfo().icon)
    builder.setOngoing(True)
    builder.setContentIntent(pending)
    notif = builder.build()
    service.startForeground(1001, notif)

# --- Pause/Resume receiver ---
class PyPauseResumeReceiver(PythonJavaClass):
    __javaclass__ = 'org/kivy/android/DashmanPauseResumeReceiver'
    __javacontext__ = 'app'

    @java_method('(Landroid/content/Context;Landroid/content/Intent;)V')
    def onReceive(self, context, intent):
        try:
            action = intent.getAction()
            if action == "com.kelev.dashman.PAUSE":
                stop_listening()
            elif action == "com.kelev.dashman.RESUME":
                start_listening()
            elif action == "com.kelev.dashman.STOP":
                shutdown()
        except Exception:
            pass

# --- Recognition listener ---
class PyRecListener(PythonJavaClass):
    __javainterfaces__ = ['android/speech/RecognitionListener']
    __javacontext__ = 'app'

    def __init__(self, on_text_cb):
        super().__init__(); self.on_text_cb = on_text_cb

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
            data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if data:
                text = (data.get(0) or "").lower()
                self.on_text_cb(text)
        except Exception:
            pass
        restart_listening_async()

    @java_method('(Landroid/os/Bundle;)V')


    def onPartialResults(self, partialResults):
        global _last_hit, _partial_buf
        try:
            data = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if not data:
                return
            text = (data.get(0) or "").lower()
            _partial_buf.append(text)
            joined = " ".join(_partial_buf[-3:])  # окно из 3 последних кусочков
            now = time.time()
            if "эй" in joined and "дашман" in joined and (now - _last_hit) > 1.5:
                _last_hit = now
                trigger_hotword_broadcast()
        except Exception:
            pass

    @java_method('(I)V')
    def onError(self, error):
        # небольшой бэкофф, затем перезапуск
        time.sleep(0.5)
        restart_listening_async()

    @java_method('(ILandroid/os/Bundle;)V')
    def onEvent(self, eventType, params): pass

# --- Control helpers ---
def on_text(text):
    if "эй" in text and "дашман" in text:
        trigger_hotword_broadcast()

def _ensure_intent(ctx):
    global _intent
    _intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    _intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    _intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
    _intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, True)
    _intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    _intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName())
    # чуть агрессивнее завершать на тишине
    _intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
    _intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500)

def start_listening():
    global _recognizer, _listener, _intent, _partial_buf
    ctx = _service
    if _recognizer is None:
        _recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
    if _listener is None:
        _listener = PyRecListener(on_text)
    _ensure_intent(ctx)
    _recognizer.setRecognitionListener(_listener)
    _partial_buf = []
    _recognizer.startListening(_intent)

def stop_listening():
    global _recognizer
    try:
        if _recognizer:
            _recognizer.stopListening()
    except Exception:
        pass

def destroy_recognizer():
    global _recognizer, _listener
    try:
        if _recognizer:
            try:
                _recognizer.cancel()
            except Exception:
                pass
            try:
                _recognizer.destroy()
            except Exception:
                pass
    finally:
        _recognizer = None
        _listener = None

def restart_listening_async():
    def _r():
        try:
            stop_listening()
        except Exception:
            pass
        time.sleep(0.2)
        try:
            start_listening()
        except Exception:
            pass
    threading.Thread(target=_r, daemon=True).start()

def trigger_hotword_broadcast():
    try:
        if _service is None:
            return
        intent = Intent("com.kelev.dashman.HOTWORD")
        _service.sendBroadcast(intent)
    except Exception:
        pass

def register_pause_resume():
    global _pause_receiver
    if _service is None:
        return
    try:
        _pause_receiver = PyPauseResumeReceiver()
        f = IntentFilter()
        f.addAction("com.kelev.dashman.PAUSE")
        f.addAction("com.kelev.dashman.RESUME")
        f.addAction("com.kelev.dashman.STOP")
        _service.registerReceiver(_pause_receiver, f)
    except Exception:
        _pause_receiver = None

def unregister_pause_resume():
    global _pause_receiver
    if _service is None or _pause_receiver is None:
        return
    try:
        _service.unregisterReceiver(_pause_receiver)
    except Exception:
        pass
    _pause_receiver = None

def shutdown():
    try:
        destroy_recognizer()
    finally:
        unregister_pause_resume()
        try:
            if _service is not None:
                _service.stopForeground(True)
        except Exception:
            pass


def main():
    global _service
    _service = PythonService.mService
    start_foreground(_service)
    register_pause_resume()
    start_listening()
    # держим процесс живым
    while True:
        time.sleep(1.0)

# --- safe entrypoint ---
def _safe_is_main():
    try:
        return (__name__ == 'main')
    except NameError:
        return True  # если name "пропал" при запуске из сервиса

if _safe_is_main():
    main()