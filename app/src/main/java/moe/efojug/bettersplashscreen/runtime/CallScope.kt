package moe.efojug.bettersplashscreen.runtime

/** Only for synchronous nested calls under makeSplashScreenContentView on the Shell worker. */
class CallScope<T : Any> {
    private val local = ThreadLocal<ArrayDeque<T>>()
    val current: T? get() = local.get()?.lastOrNull()

    fun <R> with(value: T, block: () -> R): R {
        val stack = local.get() ?: ArrayDeque<T>().also { local.set(it) }
        stack.addLast(value)
        return try {
            block()
        } finally {
            stack.removeLast()
            if (stack.isEmpty()) local.remove()
        }
    }
}
