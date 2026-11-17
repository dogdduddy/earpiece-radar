package com.dogdduddy.earpieceradar.data

import android.content.Context
import com.dogdduddy.earpieceradar.signal.Baseline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 장소 프로필: 교실/시험장 등 장소별 튠 + (선택) 저장된 영점.
 * 저장된 영점은 크기 모드에서 의미가 있다(벡터 모드는 폰 자세가 바뀌면 무효).
 */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val tuning: Tuning = TuningDefaults.DEVELOPER,
    val savedBaseline: BaselineDto? = null,
    val savedBaselineAtEpochMs: Long = 0L,
    val note: String = "",
) {
    val isDefault: Boolean get() = id == DEFAULT_ID
    companion object {
        const val DEFAULT_ID = "default"
        val DEFAULT = Profile(DEFAULT_ID, "기본값 (개발자 설정)")
    }
}

class ProfileRepository(context: Context, scope: CoroutineScope) {
    private val file = File(context.filesDir, "profiles.json")
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString("active", Profile.DEFAULT_ID) ?: Profile.DEFAULT_ID)
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    val active: StateFlow<Profile> = combine(_profiles, _activeId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.first()
    }.stateIn(scope, SharingStarted.Eagerly, _profiles.value.firstOrNull { it.id == _activeId.value } ?: Profile.DEFAULT)

    private fun load(): List<Profile> {
        val list = try {
            if (file.exists()) json.decodeFromString<List<Profile>>(file.readText()) else emptyList()
        } catch (e: Exception) { emptyList() }
        return if (list.none { it.id == Profile.DEFAULT_ID }) listOf(Profile.DEFAULT) + list else list
    }

    private fun persist(list: List<Profile>) {
        _profiles.value = list
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        tmp.renameTo(file)
    }

    fun select(id: String) {
        if (_profiles.value.none { it.id == id }) return
        _activeId.value = id
        prefs.edit().putString("active", id).apply()
    }

    fun create(name: String, from: Profile? = active.value): Profile {
        val p = Profile(
            id = UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "새 장소" },
            tuning = from?.tuning ?: TuningDefaults.DEVELOPER,
        )
        persist(_profiles.value + p)
        select(p.id)
        return p
    }

    fun update(id: String, transform: (Profile) -> Profile) {
        persist(_profiles.value.map { if (it.id == id) transform(it) else it })
    }

    fun updateActive(transform: (Profile) -> Profile) = update(active.value.id, transform)

    fun rename(id: String, name: String) = update(id) { it.copy(name = name) }

    fun delete(id: String) {
        if (id == Profile.DEFAULT_ID) return
        persist(_profiles.value.filterNot { it.id == id })
        if (_activeId.value == id) select(Profile.DEFAULT_ID)
    }

    /** 튠을 개발자 기본값으로 되돌린다(영점·이름은 유지). */
    fun resetTuning(id: String = active.value.id) = update(id) { it.copy(tuning = TuningDefaults.DEVELOPER) }

    fun saveBaseline(id: String, b: Baseline) = update(id) {
        it.copy(savedBaseline = BaselineDto.from(b), savedBaselineAtEpochMs = System.currentTimeMillis())
    }

    fun clearBaseline(id: String) = update(id) { it.copy(savedBaseline = null, savedBaselineAtEpochMs = 0L) }
}
