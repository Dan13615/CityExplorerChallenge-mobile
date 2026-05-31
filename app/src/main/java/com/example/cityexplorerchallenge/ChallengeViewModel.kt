package com.example.cityexplorerchallenge

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

class ChallengeViewModel : ViewModel() {

    private val _activeChallenge = MutableLiveData<ChallengeState?>()
    val activeChallenge: LiveData<ChallengeState?> = _activeChallenge

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun createDynamicChallenge(userLatitude: Double, userLongitude: Double, tags: List<String>) {
        _isLoading.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {
            val urlString = "https://overpass-api.de/api/interpreter"
            val subQueries = StringBuilder()

            for (tag in tags) {
                subQueries.append("node[$tag](around:1500, $userLatitude, $userLongitude);\n")
            }

            val query = """
                [out:json][timeout:25];
                (
                  $subQueries
                );
                out body 15;
            """.trimIndent()

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("User-Agent", "CityExplorerChallenge/1.0")

                connection.outputStream.use { os -> os.write(query.toByteArray(Charsets.UTF_8)) }
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val elements = jsonResponse.getJSONArray("elements")

                    if (elements.length() > 0) {
                        val randomIndex = (0 until elements.length()).random()
                        val element = elements.getJSONObject(randomIndex)
                        val lat = element.getDouble("lat")
                        val lon = element.getDouble("lon")

                        val nodeTags = element.optJSONObject("tags")
                        val placeName = nodeTags?.optString("name", "Unnamed Target Location") ?: "Unnamed Target Location"

                        val parsedCategory = when {
                            nodeTags?.has("historic") == true -> "Culture / History"
                            nodeTags?.has("amenity") == true && nodeTags.getString("amenity") == "restaurant" -> "Food / Restaurant"
                            else -> "Cafe / Relaxation"
                        }

                        withContext(Dispatchers.Main) {
                            _isLoading.value = false
                            _activeChallenge.value = ChallengeState(
                                name = placeName,
                                category = parsedCategory,
                                startPoint = GeoPoint(userLatitude, userLongitude), // Saved explicitly here
                                targetPoint = GeoPoint(lat, lon),
                                isActive = true
                            )
                        }
                    } else {
                        postErrorClear()
                    }
                } else {
                    postErrorClear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postErrorClear()
            }
        }
    }

    fun updateDistance(distanceText: String) {
        _activeChallenge.value = _activeChallenge.value?.copy(distanceText = distanceText)
    }

    private suspend fun postErrorClear() {
        withContext(Dispatchers.Main) {
            _isLoading.value = false
            _activeChallenge.value = null
        }
    }
}