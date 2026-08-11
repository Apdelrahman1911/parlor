package com.parlor.games.whodunit.content

/**
 * The complete set of Whodunit case resources packaged by this release.
 *
 * A desktop contract test compares this catalog with the JSON files on disk,
 * so adding, removing, or renaming a resource cannot silently diverge from the
 * cases exposed by production dependency injection.
 */
internal val bundledWhodunitCaseIds: List<String> = listOf(
    "last-dinner",
    "layla-halabi",
    "jasmine-ring",
    "khan-el-khalili",
    "iskenderia-corniche",
    "zamalek-ramadan",
    "saidi-inheritance",
)
