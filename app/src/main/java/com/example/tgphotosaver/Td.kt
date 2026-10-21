package com.example.tgphotosaver

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Channel(val id: Long, val title: String)

data class TgPhoto(
    val chatId: Long,
    val messageId: Long,
    val thumbFileId: Int,
    val fullFileId: Int
)

class TdException(val code: Int, message: String) : Exception(message)

sealed class Auth {
    object Starting : Auth()
    object NeedCredentials : Auth()
    object WaitPhone : Auth()
    object WaitCode : Auth()
    data class WaitPassword(val hint: String) : Auth()
    object Ready : Auth()
    data class Problem(val text: String) : Auth()
}

/** Thin wrapper around TDLib (Telegram's official client library). */
object Td {

    private const val PREFS = "td_prefs"
    private const val KEY_API_ID = "api_id"
    private const val KEY_API_HASH = "api_hash"

    private val _auth = MutableStateFlow<Auth>(Auth.Starting)
    val auth: StateFlow<Auth> = _auth

    @Volatile
    private var client: Client? = null
    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureStarted()
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun apiId(): Int = prefs().getInt(KEY_API_ID, 0)
    private fun apiHash(): String = prefs().getString(KEY_API_HASH, "").orEmpty()

    fun saveCredentials(apiId: Int, apiHash: String) {
        prefs().edit().putInt(KEY_API_ID, apiId).putString(KEY_API_HASH, apiHash).apply()
        ensureStarted()
    }

    /** Forget the API id/hash (used when the user typed them wrongly). */
    fun resetCredentials() {
        prefs().edit().clear().apply()
        val c = client
        if (c != null) {
            c.send(TdApi.Close(), Client.ResultHandler { })
        } else {
            _auth.value = Auth.NeedCredentials
        }
    }

    @Synchronized
    private fun ensureStarted() {
        if (client != null) return
        if (apiId() == 0 || apiHash().isBlank()) {
            _auth.value = Auth.NeedCredentials
            return
        }
        _auth.value = Auth.Starting
        try {
            Client.execute(TdApi.SetLogVerbosityLevel().apply { newVerbosityLevel = 1 })
        } catch (e: Throwable) {
            // logging level is optional
        }
        client = Client.create(Client.ResultHandler { obj -> handleUpdate(obj) }, null, null)
    }

    private fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            onAuthState(obj.authorizationState)
        }
    }

    private fun onAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _auth.value = Auth.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> _auth.value = Auth.WaitCode
            is TdApi.AuthorizationStateWaitPassword ->
                _auth.value = Auth.WaitPassword(state.passwordHint ?: "")
            is TdApi.AuthorizationStateReady -> _auth.value = Auth.Ready
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing -> Unit
            is TdApi.AuthorizationStateClosed -> {
                client = null
                _auth.value = Auth.Starting
                mainHandler.post { ensureStarted() }
            }
            else -> _auth.value =
                Auth.Problem("This account needs a login step the app does not support (${state.javaClass.simpleName}).")
        }
    }

    private fun sendParameters() {
        val p = TdApi.SetTdlibParameters()
        p.useTestDc = false
        p.databaseDirectory = File(appContext.filesDir, "tdlib").absolutePath
        p.filesDirectory = File(appContext.filesDir, "tdfiles").absolutePath
        p.databaseEncryptionKey = ByteArray(0)
        p.useFileDatabase = true
        p.useChatInfoDatabase = true
        p.useMessageDatabase = true
        p.useSecretChats = false
        p.apiId = apiId()
        p.apiHash = apiHash()
        p.systemLanguageCode = "en"
        p.deviceModel = Build.MODEL ?: "Android"
        p.systemVersion = Build.VERSION.RELEASE ?: "Android"
        p.applicationVersion = "1.0"
        client?.send(p, Client.ResultHandler { result ->
            if (result is TdApi.Error) {
                _auth.value = Auth.Problem("TDLib setup failed: ${result.message}")
            }
        })
    }

    /** Sends a request and returns TDLib's answer; throws [TdException] when TDLib answers with an error. */
    suspend fun request(function: TdApi.Function<*>): TdApi.Object =
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) {
                cont.resumeWithException(TdException(-1, "Not connected yet"))
                return@suspendCancellableCoroutine
            }
            c.send(function, Client.ResultHandler { result ->
                if (result is TdApi.Error) {
                    cont.resumeWithException(TdException(result.code, result.message))
                } else {
                    cont.resume(result)
                }
            })
        }

    // ---- login steps ----

    suspend fun submitPhone(phone: String) {
        val f = TdApi.SetAuthenticationPhoneNumber()
        f.phoneNumber = phone
        request(f)
    }

    suspend fun submitCode(code: String) {
        val f = TdApi.CheckAuthenticationCode()
        f.code = code
        request(f)
    }

    suspend fun submitPassword(password: String) {
        val f = TdApi.CheckAuthenticationPassword()
        f.password = password
        request(f)
    }

    suspend fun logOut() {
        request(TdApi.LogOut())
    }

    // ---- channels ----

    /** All channels the account has joined (main list and archive), sorted by title. */
    suspend fun loadChannels(): List<Channel> {
        val ids = LinkedHashSet<Long>()
        val lists = listOf<TdApi.ChatList>(TdApi.ChatListMain(), TdApi.ChatListArchive())
        for (list in lists) {
            for (i in 0 until 10) {
                try {
                    val load = TdApi.LoadChats()
                    load.chatList = list
                    load.limit = 100
                    request(load)
                } catch (e: TdException) {
                    break // 404 means everything is already loaded
                }
            }
            val get = TdApi.GetChats()
            get.chatList = list
            get.limit = 2000
            val chats = request(get) as TdApi.Chats
            for (id in chats.chatIds) ids.add(id)
        }

        val result = ArrayList<Channel>()
        for (id in ids) {
            val chat = try {
                val g = TdApi.GetChat()
                g.chatId = id
                request(g) as TdApi.Chat
            } catch (e: TdException) {
                continue
            }
            val type = chat.type
            if (type is TdApi.ChatTypeSupergroup && type.isChannel) {
                result.add(Channel(chat.id, chat.title))
            }
        }
        return result.sortedBy { it.title.lowercase() }
    }

    // ---- photos ----

    class HistoryPage(val photos: List<TgPhoto>, val oldestMessageId: Long?)

    /**
     * Reads one batch of messages older than [fromMessageId] (0 = newest) and returns the photos in it.
     * [HistoryPage.oldestMessageId] is null when there is nothing older.
     */
    suspend fun photosBefore(chatId: Long, fromMessageId: Long): HistoryPage {
        val f = TdApi.GetChatHistory()
        f.chatId = chatId
        f.fromMessageId = fromMessageId
        f.offset = 0
        f.limit = 100
        f.onlyLocal = false
        val messages = (request(f) as TdApi.Messages).messages

        val older = messages.filter { fromMessageId == 0L || it.id < fromMessageId }
        if (older.isEmpty()) return HistoryPage(emptyList(), null)

        val photos = older.mapNotNull { message ->
            val content = message.content
            if (content is TdApi.MessagePhoto) toPhoto(chatId, message.id, content.photo) else null
        }
        return HistoryPage(photos, older.minOf { it.id })
    }

    private fun toPhoto(chatId: Long, messageId: Long, photo: TdApi.Photo): TgPhoto? {
        val sizes = photo.sizes
        if (sizes.isEmpty()) return null
        val full = sizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
        val thumb = sizes.filter { it.width >= 300 }.minByOrNull { it.width } ?: full
        return TgPhoto(chatId, messageId, thumb.photo.id, full.photo.id)
    }

    /** Downloads a file into TDLib's cache and returns its local path. */
    suspend fun downloadFile(fileId: Int, priority: Int): String {
        val f = TdApi.DownloadFile()
        f.fileId = fileId
        f.priority = priority
        f.offset = 0
        f.limit = 0
        f.synchronous = true
        val file = request(f) as TdApi.File
        if (!file.local.isDownloadingCompleted) throw TdException(-2, "Download did not finish")
        return file.local.path
    }
}
