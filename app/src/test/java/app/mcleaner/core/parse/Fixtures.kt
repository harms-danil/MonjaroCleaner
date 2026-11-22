package app.mcleaner.core.parse

object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("fixtures/$name")) { "no fixture $name" }.readText()
}
