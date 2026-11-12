package moe.efojug.bettersplashscreen.compat

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class BindingException(val status: String, message: String) : Exception(message)

/** No first-int, parameter-count-only, assignable-type, or all-overloads matching. */
object ExactMembers {
    fun method(owner: Class<*>, name: String, returns: Class<*>, vararg params: Class<*>): Method {
        val candidates = owner.declaredMethods.filter {
            it.name == name && it.returnType == returns && it.parameterTypes.contentEquals(params) && !it.isBridge && !it.isSynthetic && !Modifier.isStatic(
                it.modifiers
            )
        }
        if (candidates.size != 1) {
            val available = owner.declaredMethods.filter { it.name == name }
                .joinToString(" | ") { it.toGenericString() }
            throw BindingException(
                if (candidates.isEmpty()) "missing" else "ambiguous",
                "${owner.name}#$name(${params.joinToString { it.name }}):${returns.name}; available=$available"
            )
        }
        return candidates.single().apply { isAccessible = true }
    }

    fun field(owner: Class<*>, name: String, type: Class<*>, writable: Boolean = false): Field {
        val field = try {
            owner.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            throw BindingException("missing", "${owner.name}#$name:${type.name}")
        }
        if (field.type != type || Modifier.isStatic(field.modifiers) || (writable && Modifier.isFinal(
                field.modifiers
            ))
        ) {
            throw BindingException(
                "incompatible",
                "${field.toGenericString()}; expected=${type.name} writable=$writable"
            )
        }
        return field.apply { isAccessible = true }
    }

    fun oneClass(loader: ClassLoader, vararg names: String): Class<*> {
        val found = names.mapNotNull {
            try {
                Class.forName(it, false, loader)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
        if (found.size != 1) throw BindingException(
            if (found.isEmpty()) "missing" else "ambiguous", "classes=${names.joinToString()}",
        )
        return found.single()
    }
}

/** Restore shared drawer attributes even when preparation or the original call throws. */
class FieldEdits(private val owner: Any) : AutoCloseable {
    private val originals = LinkedHashMap<Field, Any?>()

    fun set(field: Field, value: Any?) {
        if (!originals.containsKey(field)) originals[field] = field.get(owner)
        field.set(owner, value)
    }

    override fun close() {
        var failure: Exception? = null
        originals.entries.toList().asReversed().forEach { (field, value) ->
            try {
                field.set(owner, value)
            } catch (e: Exception) {
                if (failure == null) failure = e else failure!!.addSuppressed(e)
            }
        }
        originals.clear()
        failure?.let { throw it }
    }
}
