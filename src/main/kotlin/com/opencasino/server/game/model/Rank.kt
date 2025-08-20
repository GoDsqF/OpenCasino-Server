package com.opencasino.server.game.model

enum class Rank constructor(private val code: String) : Iterator<Rank?> {

    C2("2"),
    C3("3"),
    C4("4"),
    C5("5"),
    C6("6"),
    C7("7"),
    C8("8"),
    C9("9"),
    C10("10"),
    CJ("J"),
    CQ("Q"),
    CK("K"),
    CA("A");

    override operator fun next(): Rank? = if (hasNext()) ordinalLookup[this.ordinal + 1] else null

    override fun hasNext(): Boolean = this.ordinal < ordinalLookup.size - 1

    companion object {

        fun forCode(code: Char) = forCode(code.toString())

        fun forCode(code: String): Rank = codeLookup[code] ?: throw RuntimeException("No value for code: $code")

        private val codeLookup = HashMap<String, Rank>()

        private val ordinalLookup = HashMap<Int, Rank>()

        init {
            for (value in entries) {
                codeLookup[value.code] = value
                ordinalLookup[value.ordinal] = value
            }
        }
    }
}