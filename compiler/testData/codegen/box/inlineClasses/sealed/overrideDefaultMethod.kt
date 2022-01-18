// LANGUAGE: -JvmInlineValueClasses, +GenericInlineClassParameter, +SealedInlineClasses
// IGNORE_BACKEND: JVM

interface I {
    fun str(): String = "K"
}

sealed inline class IC: I

inline class ICString(val s: String): IC() {
    override fun str(): String = s
}

object ICO: IC()

fun toString(ic: IC): String = ic.str()

fun box(): String = toString(ICString("O")) + toString(ICO)