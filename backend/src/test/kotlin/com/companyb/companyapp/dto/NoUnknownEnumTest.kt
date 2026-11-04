package com.companyb.companyapp.dto

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A wire enum lists only real values. An absent value is a missing field (a 400 on input) or a
 * nullable property, never an UNKNOWN constant that every reader must remember to reject.
 */
class NoUnknownEnumTest {
    private val pkg = "com.companyb.companyapp.contracts"

    private fun contractEnums(): List<Class<*>> {
        val root = File(requireNotNull(javaClass.classLoader.getResource(pkg.replace('.', '/'))).toURI())
        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map {
                pkg + "." +
                    it
                        .relativeTo(root)
                        .path
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
            }.map { Class.forName(it) }
            .filter { it.isEnum }
            .toList()
    }

    @Test
    fun contract_enums_carry_no_unknown_value() {
        val enums = contractEnums()
        assertTrue(enums.size >= 10, "found only ${enums.size} contract enums; scan broke")
        val offenders =
            enums
                .filter { e -> e.enumConstants.any { (it as Enum<*>).name.equals("UNKNOWN", ignoreCase = true) } }
                .map { it.name }
        assertEquals(emptyList(), offenders)
    }
}
