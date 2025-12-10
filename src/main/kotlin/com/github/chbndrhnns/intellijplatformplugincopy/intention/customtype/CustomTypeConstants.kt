package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

object CustomTypeConstants {
    val SUPPORTED_BUILTINS: Set<String> = setOf(
        "int", "str", "float", "bool", "bytes",
        "datetime", "date", "time", "timedelta", "UUID", "Decimal",
        "list", "set", "dict", "tuple", "frozenset",
        "IPv4Address", "IPv6Address",
        "IPv4Network", "IPv6Network",
        "IPv4Interface", "IPv6Interface",
        "Path"
    )

    val TYPING_ALIASES: Map<String, String> = mapOf(
        "Dict" to "dict",
        "List" to "list",
        "Set" to "set",
        "Tuple" to "tuple",
        "FrozenSet" to "frozenset",
        "typing.Dict" to "dict",
        "typing.List" to "list",
        "typing.Set" to "set",
        "typing.Tuple" to "tuple",
        "typing.FrozenSet" to "frozenset"
    )

    val TYPING_SHORT_NAMES: Set<String> = setOf(
        "Dict", "List", "Set", "Tuple", "FrozenSet"
    )
}
