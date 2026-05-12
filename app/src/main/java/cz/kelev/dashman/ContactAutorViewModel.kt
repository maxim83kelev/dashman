package cz.kelev.dashman

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContactAuthorViewModel : ViewModel() {

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    fun updateMessage(text: String) {
        _message.value = text
    }

    // позже:
    // - generateReportTxt()
    // - collectLastErrors()
    // - attachReport()
    // - sendToTelegram()
}