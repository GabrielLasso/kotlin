operator fun <K, V> MutableMap<K, V>.set(k: K, v: V) {}

fun foo(a: MutableMap<String, String>, x: String?) {
    a[x!!] = x
    a[x] = x!!
}

fun foo1(a: MutableMap<String, String>, x: String?) {
    a[<!ARGUMENT_TYPE_MISMATCH!>x<!>] = x!!
    a[x!!] = x
}
