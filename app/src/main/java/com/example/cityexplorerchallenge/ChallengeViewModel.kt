package com.example.cityexplorerchallenge

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class CompletedChallenge(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val distanceText: String
)

class ChallengeViewModel : ViewModel() {

    private val _activeChallenge = MutableLiveData<ChallengeState?>()
    val activeChallenge: LiveData<ChallengeState?> = _activeChallenge

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _completedChallenges = MutableLiveData<List<CompletedChallenge>>()
    val completedChallenges: LiveData<List<CompletedChallenge>> = _completedChallenges

    private val _challengeProposals = MutableLiveData<List<ChallengeState>?>()
    val challengeProposals: LiveData<List<ChallengeState>?> = _challengeProposals

    fun createDynamicChallenge(context: Context, userLatitude: Double, userLongitude: Double, tags: List<String>) {
        _isLoading.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {
            val urlString = "https://overpass-api.de/api/interpreter"
            val subQueries = StringBuilder()

            for (tag in tags) {
                subQueries.append("nwr[$tag](around:1500, $userLatitude, $userLongitude);\n")
            }

            val query = """
                [out:json][timeout:25];
                (
                  $subQueries
                );
                out center body 15;
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
                        
                        val lat = if (element.has("lat")) {
                            element.getDouble("lat")
                        } else {
                            element.getJSONObject("center").getDouble("lat")
                        }
                        
                        val lon = if (element.has("lon")) {
                            element.getDouble("lon")
                        } else {
                            element.getJSONObject("center").getDouble("lon")
                        }

                        val nodeTags = element.optJSONObject("tags")
                        val placeName = nodeTags?.optString("name", "Unnamed Target Location") ?: "Unnamed Target Location"

                        val parsedCategory = when {
                            nodeTags?.has("historic") == true -> "Culture / History"
                            nodeTags?.has("amenity") == true && nodeTags.getString("amenity") == "restaurant" -> "Food / Restaurant"
                            nodeTags?.has("leisure") == true && nodeTags.getString("leisure") == "park" -> "Nature / Park"
                            nodeTags?.has("tourism") == true && nodeTags.getString("tourism") == "attraction" -> "Tourist Attraction"
                            else -> "Cafe / Relaxation"
                        }

                        withContext(Dispatchers.Main) {
                            _isLoading.value = false
                            val newState = ChallengeState(
                                name = placeName,
                                category = parsedCategory,
                                startPoint = GeoPoint(userLatitude, userLongitude),
                                targetPoint = GeoPoint(lat, lon),
                                isActive = true
                            )
                            _activeChallenge.value = newState
                            saveActiveChallenge(context, newState)
                        }
                    } else {
                        postErrorClear(context)
                    }
                } else {
                    postErrorClear(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postErrorClear(context)
            }
        }
    }

    fun updateDistance(context: Context, distanceText: String) {
        val updated = _activeChallenge.value?.copy(distanceText = distanceText)
        _activeChallenge.value = updated
        updated?.let { saveActiveChallenge(context, it) }
    }

    fun completeActiveChallenge(context: Context) {
        val challenge = _activeChallenge.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("completed_challenges", Context.MODE_PRIVATE)
            val completedChallengesJson = sharedPrefs.getString("completed_list", "[]") ?: "[]"

            val jsonArray = JSONArray(completedChallengesJson)
            val challengeJson = JSONObject().apply {
                put("name", challenge.name)
                put("category", challenge.category)
                put("lat", challenge.targetPoint.latitude)
                put("lon", challenge.targetPoint.longitude)
                put("timestamp", System.currentTimeMillis())
                put("distance", challenge.distanceText)
            }
            jsonArray.put(challengeJson)

            sharedPrefs.edit().apply {
                putString("completed_list", jsonArray.toString())
                remove("active_challenge")
            }.apply()

            loadHistory(context) // Refresh history after completion

            withContext(Dispatchers.Main) {
                _activeChallenge.value = null
            }
        }
    }

    fun loadHistory(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("completed_challenges", Context.MODE_PRIVATE)
            val jsonString = sharedPrefs.getString("completed_list", "[]") ?: "[]"
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<CompletedChallenge>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CompletedChallenge(
                        obj.getString("name"),
                        obj.getString("category"),
                        obj.getDouble("lat"),
                        obj.getDouble("lon"),
                        obj.getLong("timestamp"),
                        obj.optString("distance", "--")
                    )
                )
            }
            // Sort by most recent
            list.sortByDescending { it.timestamp }
            _completedChallenges.postValue(list)
        }
    }

    fun loadActiveChallenge(context: Context) {
        val sharedPrefs = context.getSharedPreferences("completed_challenges", Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString("active_challenge", null) ?: return
        try {
            val json = JSONObject(jsonString)
            val state = ChallengeState(
                name = json.getString("name"),
                category = json.getString("category"),
                startPoint = GeoPoint(json.getDouble("start_lat"), json.getDouble("start_lon")),
                targetPoint = GeoPoint(json.getDouble("target_lat"), json.getDouble("target_lon")),
                distanceText = json.getString("distance"),
                isActive = json.getBoolean("isActive")
            )
            _activeChallenge.postValue(state)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setActiveChallenge(context: Context, challenge: ChallengeState) {
        _activeChallenge.value = challenge
        saveActiveChallenge(context, challenge)
        clearProposals()
    }

    fun clearProposals() {
        _challengeProposals.value = null
    }

    fun proposeRandomChallenges(context: Context, userLat: Double, userLon: Double) {
        _isLoading.postValue(true)
        
        viewModelScope.launch(Dispatchers.IO) {
            val history = _completedChallenges.value ?: emptyList()
            
            val allCategories = listOf("Culture / History", "Food / Restaurant", "Nature / Park", "Tourist Attraction", "Cafe / Relaxation")
            val counts = history.groupingBy { it.category }.eachCount()
            
            val leastVisitedCategory = allCategories.minByOrNull { counts.getOrDefault(it, 0) }
            val mostVisitedCategory = allCategories.maxByOrNull { counts.getOrDefault(it, 0) }

            val tags = listOf("historic", "amenity=restaurant", "leisure=park", "tourism=attraction", "amenity=cafe")
            val urlString = "https://overpass-api.de/api/interpreter"
            val subQueries = StringBuilder()
            for (tag in tags) {
                subQueries.append("nwr[$tag](around:2500, $userLat, $userLon);\n")
            }

            val query = """
                [out:json][timeout:30];
                (
                  $subQueries
                );
                out center body 50;
            """.trimIndent()

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("User-Agent", "CityExplorerChallenge/1.0")
                connection.outputStream.use { os -> os.write(query.toByteArray(Charsets.UTF_8)) }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val elements = JSONObject(response).getJSONArray("elements")
                    val rawProposals = mutableListOf<ChallengeState>()
                    val userGeoPoint = GeoPoint(userLat, userLon)
                    
                    for (i in 0 until elements.length()) {
                        val element = elements.getJSONObject(i)
                        val lat = if (element.has("lat")) element.getDouble("lat") else element.getJSONObject("center").getDouble("lat")
                        val lon = if (element.has("lon")) element.getDouble("lon") else element.getJSONObject("center").getDouble("lon")
                        val nodeTags = element.optJSONObject("tags")
                        val placeName = nodeTags?.optString("name", "Mystery Location") ?: "Mystery Location"
                        
                        val cat = when {
                            nodeTags?.has("leisure") == true && nodeTags.getString("leisure") == "park" -> "Nature / Park"
                            nodeTags?.has("historic") == true -> "Culture / History"
                            nodeTags?.has("tourism") == true -> "Tourist Attraction"
                            nodeTags?.has("amenity") == true && nodeTags.getString("amenity") == "restaurant" -> "Food / Restaurant"
                            else -> "Cafe / Relaxation"
                        }

                        val distance = GeoPoint(lat, lon).distanceToAsDouble(userGeoPoint)
                        if (distance < 500.0) continue // Only propose challenges at least 500m away

                        val distText = if (distance >= 1000) {
                            String.format(Locale.getDefault(), "%.2f km", distance / 1000.0)
                        } else {
                            String.format(Locale.getDefault(), "%d m", distance.toInt())
                        }

                        rawProposals.add(ChallengeState(
                            name = placeName,
                            category = cat,
                            startPoint = userGeoPoint,
                            targetPoint = GeoPoint(lat, lon),
                            distanceText = distText,
                            isActive = false
                        ))
                    }

                    // Algorithm logic
                    val nearestMostVisited = rawProposals
                        .filter { it.category == mostVisitedCategory }
                        .minByOrNull { it.targetPoint.distanceToAsDouble(userGeoPoint) }

                    val finalProposals = mutableListOf<ChallengeState>()
                    
                    // 1. Add nearest from liked category if exists
                    nearestMostVisited?.let {
                        finalProposals.add(it.copy(selectionReason = "You love this category! This is the closest discovery of its kind near you."))
                    }

                    // 2. Add others, prioritizing least visited
                    val remainingPool = rawProposals.filter { it.name != nearestMostVisited?.name }.shuffled()
                    
                    val leastVisitedPool = remainingPool.filter { it.category == leastVisitedCategory }
                    val othersPool = remainingPool.filter { it.category != leastVisitedCategory }

                    for (p in leastVisitedPool) {
                        if (finalProposals.size >= 10) break
                        finalProposals.add(p.copy(selectionReason = "This category is the one you've explored the least. Broaden your horizons!"))
                    }

                    for (p in othersPool) {
                        if (finalProposals.size >= 10) break
                        finalProposals.add(p.copy(selectionReason = "A new adventure waiting for you nearby."))
                    }

                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _challengeProposals.value = finalProposals
                    }
                } else {
                    postErrorClear(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postErrorClear(context)
            }
        }
    }

    private fun saveActiveChallenge(context: Context, state: ChallengeState) {
        val sharedPrefs = context.getSharedPreferences("completed_challenges", Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("name", state.name)
            put("category", state.category)
            put("start_lat", state.startPoint.latitude)
            put("start_lon", state.startPoint.longitude)
            put("target_lat", state.targetPoint.latitude)
            put("target_lon", state.targetPoint.longitude)
            put("distance", state.distanceText)
            put("isActive", state.isActive)
        }
        sharedPrefs.edit().putString("active_challenge", json.toString()).apply()
    }

    private suspend fun postErrorClear(context: Context) {
        withContext(Dispatchers.Main) {
            _isLoading.value = false
            _activeChallenge.value = null
            context.getSharedPreferences("completed_challenges", Context.MODE_PRIVATE)
                .edit().remove("active_challenge").apply()
        }
    }
}
