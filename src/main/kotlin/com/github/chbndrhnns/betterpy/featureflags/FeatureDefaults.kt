package com.github.chbndrhnns.betterpy.featureflags

object FeatureDefaults {
    private val declarationsById: Map<String, FeatureDeclaration> by lazy {
        val declarations = FeatureCatalogLoader.load()
        declarations.associateBy { it.id }
    }

    fun defaultEnabled(id: String): Boolean {
        val declaration = declarationsById[id]
            ?: error("Feature declaration not found for id: $id")
        return declaration.defaultEnabled &&
            FeatureAvailability.isAvailable(declaration.minBuild, declaration.bundledIn)
    }
}
