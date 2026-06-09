package com.example.cityexplorerchallenge

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AuthViewModel : ViewModel() {

    private val _authState = MutableLiveData<AuthResult>()
    val authState: LiveData<AuthResult> = _authState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val AUTH_API_URL = BuildConfig.AUTH_API_URL

    fun login(username: String, password: String) {
        performAuthAction(username, password, "login")
    }

    fun register(username: String, password: String) {
        performAuthAction(username, password, "register")
    }

    private fun performAuthAction(username: String, password: String, action: String) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$AUTH_API_URL/$action")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "CityExplorer/1.0")
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doOutput = true

                val jsonInputString = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }.toString()

                val input = jsonInputString.toByteArray(Charsets.UTF_8)
                
                connection.outputStream.use { os ->
                    os.write(input)
                    os.flush()
                }

                val responseCode = connection.responseCode
                val responseText = try {
                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "{\"status\":\"error\",\"message\":\"Server returned code $responseCode\"}"
                    }
                } catch (e: Exception) {
                    "{\"status\":\"error\",\"message\":\"Error reading response: ${e.message}\"}"
                } finally {
                    connection.disconnect()
                }

                val jsonResponse = JSONObject(responseText)
                val status = jsonResponse.optString("status")

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    if (status == "success") {
                        val returnedUsername = jsonResponse.optString("username", username)
                        _authState.value = AuthResult.Success(returnedUsername)
                    } else {
                        val errorMessage = jsonResponse.optString("message", "Authentication failed")
                        _authState.value = AuthResult.Error(errorMessage)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _authState.value = AuthResult.Error("Network error: ${e.message ?: "Please try again later."}")
                }
            }
        }
    }

    sealed class AuthResult {
        data class Success(val username: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }
}
