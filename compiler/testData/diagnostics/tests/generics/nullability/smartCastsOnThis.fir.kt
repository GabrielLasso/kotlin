// !DIAGNOSTICS: -UNUSED_EXPRESSION,-UNUSED_VARIABLE

fun <T : CharSequence?> T.bar1() {}
fun CharSequence?.bar2() {}

fun <T : CharSequence> T.bar3() {}
fun CharSequence.bar4() {}

fun <T : String?> T.foo() {
    if (this != null) {
        if (<!SENSELESS_COMPARISON!>this != null<!>) {}

        length
        this?.length

        bar1()
        bar2()
        bar3()
        bar4()


        this?.bar1()
    }

    <!UNSAFE_CALL!>length<!>

    if (this is String) {
        length
        this?.length

        bar1()
        bar2()
        bar3()
    }
}
