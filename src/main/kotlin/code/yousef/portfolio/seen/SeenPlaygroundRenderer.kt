package code.yousef.portfolio.seen

import code.yousef.portfolio.ssr.HYDRATION_SCRIPT_PATH
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ui.seen.SeenPlaygroundPage
import codes.yousef.summon.seo.HeadScope

const val SEEN_PLAYGROUND_MAX_CODE_LENGTH = 10_000
const val SEEN_PLAYGROUND_MAX_FORM_BYTES = 128_000

data class SeenPlaygroundViewState(
    val language: String,
    val sample: String,
    val code: String,
    val result: ExecutionResult? = null,
    val validationMessage: String? = null,
)

data class SeenPlaygroundOption(
    val value: String,
    val label: String,
)

internal object SeenPlaygroundCatalog {
    val languages = listOf(
        SeenPlaygroundOption("en", "English"),
        SeenPlaygroundOption("ar", "العربية"),
        SeenPlaygroundOption("es", "Español"),
        SeenPlaygroundOption("ru", "Русский"),
        SeenPlaygroundOption("zh", "中文"),
        SeenPlaygroundOption("ja", "日本語"),
    )

    private data class Sample(
        val id: String,
        val label: String,
        val code: String,
    )

    private val samplesByLanguage = mapOf(
        "en" to listOf(
            Sample(
                "hello-world",
                "Hello World",
                """
                    fun main() {
                        println("Hello, World!")
                    }
                """.trimIndent(),
            ),
            Sample(
                "fibonacci",
                "Fibonacci",
                """
                    fun fib(n: Int): Int {
                        if n <= 1 {
                            return n
                        }
                        return fib(n - 1) + fib(n - 2)
                    }

                    fun main() {
                        var i = 0
                        while i < 15 {
                            println(fib(i))
                            i = i + 1
                        }
                    }
                """.trimIndent(),
            ),
            Sample(
                "fizz-buzz",
                "FizzBuzz",
                """
                    fun main() {
                        var i = 1
                        while i <= 30 {
                            if i % 15 == 0 {
                                println("FizzBuzz")
                            } else {
                                if i % 3 == 0 {
                                    println("Fizz")
                                } else {
                                    if i % 5 == 0 {
                                        println("Buzz")
                                    } else {
                                        println(i)
                                    }
                                }
                            }
                            i = i + 1
                        }
                    }
                """.trimIndent(),
            ),
            Sample(
                "classes",
                "Classes",
                """
                    class Point {
                        var x: Int
                        var y: Int

                        fun new(x: Int, y: Int): Point {
                            this.x = x
                            this.y = y
                            return this
                        }

                        fun distanceTo(other: Point): Float {
                            let dx = this.x - other.x
                            let dy = this.y - other.y
                            return sqrt(toFloat(dx * dx + dy * dy))
                        }

                        fun toString(): String {
                            return "(" + toStr(this.x) + ", " + toStr(this.y) + ")"
                        }
                    }

                    fun main() {
                        let a = Point.new(0, 0)
                        let b = Point.new(3, 4)
                        println("Point A: " + a.toString())
                        println("Point B: " + b.toString())
                        println("Distance: " + toStr(a.distanceTo(b)))
                    }
                """.trimIndent(),
            ),
            Sample(
                "factorial",
                "Factorial",
                """
                    fun factorial(n: Int): Int {
                        if n <= 1 {
                            return 1
                        }
                        return n * factorial(n - 1)
                    }

                    fun main() {
                        var i = 0
                        while i <= 12 {
                            println(toStr(i) + "! = " + toStr(factorial(i)))
                            i = i + 1
                        }
                    }
                """.trimIndent(),
            ),
        ),
        "ar" to listOf(
            Sample(
                "hello-world",
                "مرحبا بالعالم",
                """
                    دالة رئيسية() {
                        اطبع("مرحبا بالعالم!")
                    }
                """.trimIndent(),
            ),
            Sample(
                "fibonacci",
                "فيبوناتشي",
                """
                    دالة فيب(ن: عدد): عدد {
                        إذا ن <= 1 {
                            رجع ن
                        }
                        رجع فيب(ن - 1) + فيب(ن - 2)
                    }

                    دالة رئيسية() {
                        متغير ع = 0
                        بينما ع < 15 {
                            اطبع(فيب(ع))
                            ع = ع + 1
                        }
                    }
                """.trimIndent(),
            ),
            Sample(
                "classes",
                "فئات",
                """
                    فئة نقطة {
                        متغير س: عدد
                        متغير ص: عدد

                        دالة جديد(س: عدد, ص: عدد): نقطة {
                            هذا.س = س
                            هذا.ص = ص
                            رجع هذا
                        }

                        دالة إلى_نص(): نص {
                            رجع "(" + إلى_نص(هذا.س) + ", " + إلى_نص(هذا.ص) + ")"
                        }
                    }

                    دالة رئيسية() {
                        اجعل أ = نقطة.جديد(3, 4)
                        اطبع("النقطة: " + أ.إلى_نص())
                    }
                """.trimIndent(),
            ),
        ),
        "es" to listOf(
            Sample(
                "hello-world",
                "Hola Mundo",
                """
                    función principal() {
                        imprimir("Hola, Mundo!")
                    }
                """.trimIndent(),
            ),
            Sample(
                "fibonacci",
                "Fibonacci",
                """
                    función fib(n: entero): entero {
                        si n <= 1 {
                            retornar n
                        }
                        retornar fib(n - 1) + fib(n - 2)
                    }

                    función principal() {
                        variable i = 0
                        mientras i < 15 {
                            imprimir(fib(i))
                            i = i + 1
                        }
                    }
                """.trimIndent(),
            ),
            Sample(
                "classes",
                "Clases",
                """
                    clase Punto {
                        variable x: entero
                        variable y: entero

                        función nuevo(x: entero, y: entero): Punto {
                            esto.x = x
                            esto.y = y
                            retornar esto
                        }

                        función aTexto(): cadena {
                            retornar "(" + aTexto(esto.x) + ", " + aTexto(esto.y) + ")"
                        }
                    }

                    función principal() {
                        sea a = Punto.nuevo(3, 4)
                        imprimir("Punto: " + a.aTexto())
                    }
                """.trimIndent(),
            ),
        ),
        "ru" to listOf(
            Sample(
                "hello-world",
                "Привет Мир",
                """
                    функция главная() {
                        печать("Привет, Мир!")
                    }
                """.trimIndent(),
            ),
            Sample(
                "fibonacci",
                "Фибоначчи",
                """
                    функция фиб(н: целое): целое {
                        если н <= 1 {
                            вернуть н
                        }
                        вернуть фиб(н - 1) + фиб(н - 2)
                    }

                    функция главная() {
                        переменная и = 0
                        пока и < 15 {
                            печать(фиб(и))
                            и = и + 1
                        }
                    }
                """.trimIndent(),
            ),
            Sample(
                "classes",
                "Классы",
                """
                    класс Точка {
                        переменная х: целое
                        переменная у: целое

                        функция новый(х: целое, у: целое): Точка {
                            это.х = х
                            это.у = у
                            вернуть это
                        }

                        функция в_строку(): строка {
                            вернуть "(" + в_строку(это.х) + ", " + в_строку(это.у) + ")"
                        }
                    }

                    функция главная() {
                        пусть а = Точка.новый(3, 4)
                        печать("Точка: " + а.в_строку())
                    }
                """.trimIndent(),
            ),
        ),
        "zh" to listOf(
            Sample(
                "hello-world",
                "你好世界",
                """
                    函数 主函数() {
                        打印("你好，世界！")
                    }
                """.trimIndent(),
            ),
            Sample(
                "fibonacci",
                "斐波那契",
                """
                    函数 斐波(数: 整数): 整数 {
                        如果 数 <= 1 {
                            返回 数
                        }
                        返回 斐波(数 - 1) + 斐波(数 - 2)
                    }

                    函数 主函数() {
                        变量 我 = 0
                        当 我 < 15 {
                            打印(斐波(我))
                            我 = 我 + 1
                        }
                    }
                """.trimIndent(),
            ),
            Sample(
                "classes",
                "类",
                """
                    类 点 {
                        变量 横: 整数
                        变量 纵: 整数

                        函数 新建(横: 整数, 纵: 整数): 点 {
                            此.横 = 横
                            此.纵 = 纵
                            返回 此
                        }

                        函数 转文本(): 字符串 {
                            返回 "(" + 转文本(此.横) + ", " + 转文本(此.纵) + ")"
                        }
                    }

                    函数 主函数() {
                        让 甲 = 点.新建(3, 4)
                        打印("点: " + 甲.转文本())
                    }
                """.trimIndent(),
            ),
        ),
        "ja" to listOf(
            Sample(
                "hello-world",
                "こんにちは世界",
                """
                    関数 メイン() {
                        表示("こんにちは、世界！")
                    }
                """.trimIndent(),
            ),
            Sample(
                "fibonacci",
                "フィボナッチ",
                """
                    関数 フィボ(数: 整数): 整数 {
                        もし 数 <= 1 {
                            戻る 数
                        }
                        戻る フィボ(数 - 1) + フィボ(数 - 2)
                    }

                    関数 メイン() {
                        変数 我 = 0
                        間 我 < 15 {
                            表示(フィボ(我))
                            我 = 我 + 1
                        }
                    }
                """.trimIndent(),
            ),
            Sample(
                "classes",
                "クラス",
                """
                    クラス 点 {
                        変数 横: 整数
                        変数 縦: 整数

                        関数 新規(横: 整数, 縦: 整数): 点 {
                            これ.横 = 横
                            これ.縦 = 縦
                            戻る これ
                        }

                        関数 文字列化(): 文字列 {
                            戻る "(" + 文字列化(これ.横) + ", " + 文字列化(これ.縦) + ")"
                        }
                    }

                    関数 メイン() {
                        定数 甲 = 点.新規(3, 4)
                        表示("点: " + 甲.文字列化())
                    }
                """.trimIndent(),
            ),
        ),
    )

    fun normalizeLanguage(value: String?): String =
        value?.takeIf(samplesByLanguage::containsKey) ?: "en"

    fun sampleOptions(language: String): List<SeenPlaygroundOption> =
        samplesFor(language).map { SeenPlaygroundOption(it.id, it.label) }

    fun state(
        language: String? = null,
        sample: String? = null,
        code: String? = null,
        result: ExecutionResult? = null,
        validationMessage: String? = null,
    ): SeenPlaygroundViewState {
        val normalizedLanguage = normalizeLanguage(language)
        val normalizedSample = samplesFor(normalizedLanguage)
            .firstOrNull { it.id == sample }
            ?: samplesFor(normalizedLanguage).first()
        return SeenPlaygroundViewState(
            language = normalizedLanguage,
            sample = normalizedSample.id,
            code = code ?: normalizedSample.code,
            result = result,
            validationMessage = validationMessage,
        )
    }

    private fun samplesFor(language: String): List<Sample> =
        samplesByLanguage[normalizeLanguage(language)].orEmpty()
}

class SeenPlaygroundRenderer(
    private val packagesEnabled: Boolean = false,
) {
    fun playgroundPage(
        state: SeenPlaygroundViewState = SeenPlaygroundCatalog.state(),
    ): SummonPage = SummonPage(
        head = playgroundHeadBlock(),
        content = {
            SeenPlaygroundPage(
                state = state,
                packagesUrl = if (packagesEnabled) "/packages" else null,
            )
        },
    )

    private fun playgroundHeadBlock(): (HeadScope) -> Unit = { head ->
        head.title("Seen Playground - Try Seen Programming Language")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta(null, "og:title", "Seen Playground", null, null)
        head.meta(
            null,
            "og:description",
            "Write and run Seen programs with the server-native playground.",
            null,
            null,
        )
        head.meta(null, "og:type", "website", null, null)
        head.meta(
            "description",
            null,
            "Try the Seen programming language with a server-rendered Summon and Aether playground.",
            null,
            null,
        )
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
    }
}
