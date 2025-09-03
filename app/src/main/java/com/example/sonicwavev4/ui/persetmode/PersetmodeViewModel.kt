package com.example.sonicwavev4.ui.persetmode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PersetmodeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is persetmode Fragment"
    }
    val text: LiveData<String> = _text
}