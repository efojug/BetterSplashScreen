package moe.efojug.bettersplashscreen

import moe.efojug.bettersplashscreen.compat.BindingException
import moe.efojug.bettersplashscreen.compat.ExactMembers
import moe.efojug.bettersplashscreen.compat.FieldEdits
import moe.efojug.bettersplashscreen.runtime.CallScope
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeContractTest {
    class Fixture {
        @JvmField var color: Int = 7
        @JvmField var label: String? = "original"
        @JvmField val immutable: Int = 9
        fun build(value: Int): Int = value
        fun build(value: String): String = value
    }
    @Test fun bindingRequiresFullSignatureAndWritableFieldType() {
        val owner = Fixture::class.java
        val int = Int::class.javaPrimitiveType!!
        assertEquals(int, ExactMembers.method(owner, "build", int, int).returnType)
        val wrongReturn = assertThrows(BindingException::class.java) {
            ExactMembers.method(owner, "build", String::class.java, int)
        }
        assertEquals("missing", wrongReturn.status)
        assertThrows(BindingException::class.java) { ExactMembers.field(owner, "color", String::class.java, true) }
        assertThrows(BindingException::class.java) { ExactMembers.field(owner, "immutable", int, true) }
        assertThrows(BindingException::class.java) {
            ExactMembers.oneClass(javaClass.classLoader!!, "java.lang.String", "java.lang.Integer")
        }
    }
    @Test fun partialPreparationAndOriginalExceptionBothRestoreFields() {
        val fixture = Fixture()
        val color = ExactMembers.field(Fixture::class.java, "color", Int::class.javaPrimitiveType!!, true)
        val label = ExactMembers.field(Fixture::class.java, "label", String::class.java, true)
        assertThrows(IllegalStateException::class.java) {
            FieldEdits(fixture).use {
                it.set(color, 100)
                it.set(color, 200)
                it.set(label, null)
                throw IllegalStateException("original failed")
            }
        }
        assertEquals(7, fixture.color)
        assertEquals("original", fixture.label)
        FieldEdits(fixture).also { it.close(); it.close() }
    }
    @Test fun nestedCallsAndExceptionsCannotLeaveThePreviousAppInScope() {
        val scope = CallScope<String>()
        scope.with("A") {
            assertEquals("A", scope.current)
            assertThrows(IllegalStateException::class.java) {
                scope.with("B") { assertEquals("B", scope.current); error("cancel") }
            }
            assertEquals("A", scope.current)
        }
        assertNull(scope.current)
    }
    @Test fun simultaneousBuildsCannotReadEachOthersIdentity() {
        val scope = CallScope<String>()
        val ready = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = listOf("A", "B").map { id -> executor.submit<String> {
                scope.with(id) {
                    ready.countDown()
                    check(ready.await(5, TimeUnit.SECONDS))
                    check(scope.current == id)
                    id
                }.also { check(scope.current == null) }
            } }
            assertEquals(listOf("A", "B"), results.map { it.get(10, TimeUnit.SECONDS) })
            assertNull(scope.current)
        } finally { executor.shutdownNow() }
    }
}
