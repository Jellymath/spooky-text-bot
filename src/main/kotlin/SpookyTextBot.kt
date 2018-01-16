import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Please specify properties file")
        return
    }
    val properties = Properties().apply {
        FileInputStream(args[0]).use { load(it) }
    }
    val knownConfigsJson = with(File("known_configs.json")) { if (exists()) readText() else null }
    val json = Gson()
    val knownConfigs = if (knownConfigsJson != null && knownConfigsJson.isNotBlank()) {
        json.fromJson<MutableMap<Int, Config>>(knownConfigsJson)
    } else {
        hashMapOf()
    }

    timer(period = TimeUnit.SECONDS.toMillis(15)) {
        File("known_configs.json").createNewFile()
        FileOutputStream("known_configs.json").bufferedWriter().use {
            it.write(json.toJson(knownConfigs))
        }
    }

    ApiContextInitializer.init()
    val bot = SpookyTextBot(properties.getProperty("token"), properties.getProperty("name"), knownConfigs)
    TelegramBotsApi().registerBot(bot)
}

class SpookyTextBot(private val token: String, private val name: String, private val configs: MutableMap<Int, Config>) : TelegramLongPollingBot() {
    override fun getBotToken() = token

    override fun getBotUsername() = name

    override fun onUpdateReceived(update: Update?) {
        update?.inlineQuery?.let {
            val (levels, effectiveness, keepOldSpooky) = configs[it.from.id] ?: Config()
            val result = (if (it.query.isNullOrBlank()) "Some spooky text for Telegram" else it.query).applySpooky(levels, effectiveness, keepOldSpooky)
            execute(AnswerInlineQuery().apply {
                inlineQueryId = it.id
                setResults(InlineQueryResultArticle().apply {
                    id = "1"
                    title = result
                    inputMessageContent = InputTextMessageContent().apply { messageText = result }
                })
            })
        }
        update?.message?.let {
            if (!it.isUserMessage && it.text?.contains("@$name") != true) {
                return
            }
            val text = it.text
            val config = configs[it.from.id] ?: Config()
            val updated = when {
                text.startsWith("/use_max_spooky_effectiveness") -> config.copy(effectiveness = SpookyEffectiveness.MAX)
                text.startsWith("/use_normal_spooky_effectiveness") -> config.copy(effectiveness = SpookyEffectiveness.NORMAL)
                text.startsWith("/use_min_spooky_effectiveness") -> config.copy(effectiveness = SpookyEffectiveness.MIN)
                text.startsWith("/add_up_symbols") -> config.copy(levels = config.levels + SpookyLevel.UP)
                text.startsWith("/add_down_symbols") -> config.copy(levels = config.levels + SpookyLevel.DOWN)
                text.startsWith("/add_mid_symbols") -> config.copy(levels = config.levels + SpookyLevel.MID)
                text.startsWith("/remove_up_symbols") -> config.copy(levels = config.levels - SpookyLevel.UP)
                text.startsWith("/remove_down_symbols") -> config.copy(levels = config.levels - SpookyLevel.DOWN)
                text.startsWith("/remove_mid_symbols") -> config.copy(levels = config.levels - SpookyLevel.MID)
                text.startsWith("/keep_spooky_symbols") -> config.copy(keepSpookySymbols = true)
                text.startsWith("/clear_spooky_symbols") -> config.copy(keepSpookySymbols = false)
                else -> null
            }
            if (updated != null) {
                configs[it.from.id] = updated
                execute(SendMessage(it.chatId, "Config updated, current config: $updated"))
            }
        }
    }
}

data class Config(val levels: Set<SpookyLevel> = setOf(SpookyLevel.MID),
                  val effectiveness: SpookyEffectiveness = SpookyEffectiveness.NORMAL,
                  val keepSpookySymbols: Boolean = false)

private fun String.applySpooky(levels: Set<SpookyLevel>,
                               effectiveness: SpookyEffectiveness,
                               keepOldSpooky: Boolean): String {
    val startString = if (keepOldSpooky) this else this.filterNot { it in upSymbols || it in downSymbols || it in midSymbols }
    return startString.map {
        buildString {
            append(it)
            levels.forEach { level: SpookyLevel ->
                val n = effectiveness.numberOfSpookyChars() / level.modifier
                repeat(n) { append(level.randomChar()) }
            }
        }
    }.joinToString("")
}

private val upSymbols = arrayOf(
        '̍', '̎', '̄', '̅',
        '̿', '̑', '̆', '̐',
        '͒', '͗', '͑', '̇',
        '̈', '̊', '͂', '̓',
        '̈́', '͊', '͋', '͌',
        '̃', '̂', '̌', '͐',
        '̀', '́', '̋', '̏',
        '̒', '̓', '̔', '̽',
        '̉', 'ͣ', 'ͤ', 'ͥ',
        'ͦ', 'ͧ', 'ͨ', 'ͩ',
        'ͪ', 'ͫ', 'ͬ', 'ͭ',
        'ͮ', 'ͯ', '̾', '͛',
        '͆', '̚'
)

private val downSymbols = arrayOf(
        '̖', '̗', '̘', '̙',
        '̜', '̝', '̞', '̟',
        '̠', '̤', '̥', '̦',
        '̩', '̪', '̫', '̬',
        '̭', '̮', '̯', '̰',
        '̱', '̲', '̳', '̹',
        '̺', '̻', '̼', 'ͅ',
        '͇', '͈', '͉', '͍',
        '͎', '͓', '͔', '͕',
        '͖', '͙', '͚', '̣'
)

private val midSymbols = arrayOf(
        '̕', '̛', '̀', '́',
        '͘', '̡', '̢', '̧',
        '̨', '̴', '̵', '̶',
        '͏', '͜', '͝', '͞',
        '͟', '͠', '͢', '̸',
        '̷', '͡', '_'
)

enum class SpookyLevel(private val dictionary: Array<Char>, val modifier: Int) {
    UP(upSymbols, 1), DOWN(downSymbols, 1), MID(midSymbols, 2);

    private val random = Random()
    fun randomChar() = dictionary[random.nextInt(dictionary.size)]
}

enum class SpookyEffectiveness(private val range: Int, private val offset: Int) {
    MIN(8, 0),
    NORMAL(8, 1),
    MAX(16, 1);

    private val random = Random()
    fun numberOfSpookyChars() = offset + random.nextInt(range)
}
