package com.cookpad.android.minicookpad

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RecipeCreateViewModel : ViewModel() {
    private val _imageUri = MutableLiveData<String>()

    val imageUri: LiveData<String> = _imageUri

    fun updateImageUri(uri: String) {
        _imageUri.postValue(uri)
    }
}
