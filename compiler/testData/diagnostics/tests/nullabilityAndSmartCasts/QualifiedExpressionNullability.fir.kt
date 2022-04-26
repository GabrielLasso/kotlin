// !DIAGNOSTICS: -DEBUG_INFO_SMARTCAST
class Foo {
    fun foo(a: Foo): Foo = a
    var f: Foo? = null
}

fun main() {
    val x: Foo? = null
    val y: Foo? = null

    x<!UNSAFE_CALL!>.<!>foo(y)
    x!!.foo(<!ARGUMENT_TYPE_MISMATCH!>y<!>)
    x.foo(y!!)
    x!!.foo(y!!)

    val a: Foo? = null
    val b: Foo? = null
    val c: Foo? = null

    a<!UNSAFE_CALL!>.<!>foo(b<!UNSAFE_CALL!>.<!>foo(c))
    a!!.foo(b<!UNSAFE_CALL!>.<!>foo(c))
    a.foo(b!!.foo(<!ARGUMENT_TYPE_MISMATCH!>c<!>))
    a!!.foo(b!!.foo(<!ARGUMENT_TYPE_MISMATCH!>c<!>))
    a.foo(b.foo(c!!))
    a!!.foo(b.foo(c!!))
    a.foo(b!!.foo(c!!))
    a!!.foo(b!!.foo(c!!))

    val z: Foo? = null
    z!!.foo(z!!)

    val w: Foo? = null
    w<!UNSAFE_CALL!>.<!>f = z
    (w<!UNSAFE_CALL!>.<!>f) = z
    (label@ w<!UNSAFE_CALL!>.<!>f) = z
    w!!.f = z
    w.f = z
    w!!.f = z
    w.f<!UNSAFE_CALL!>.<!>f = z
    w.f!!.f = z
}
