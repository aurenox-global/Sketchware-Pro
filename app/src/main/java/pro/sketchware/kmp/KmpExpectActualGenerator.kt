package pro.sketchware.kmp

class KmpPlatformAwareBlockParameter(
    @JvmField val name: String,
    @JvmField val type: String
)

class KmpPlatformAwareBlockContract(
    @JvmField val id: String,
    @JvmField val functionName: String,
    @JvmField val parameters: List<KmpPlatformAwareBlockParameter>,
    @JvmField val returnType: String,
    @JvmField val androidImplementation: String?,
    @JvmField val desktopImplementation: String?,
    @JvmField val fallbackImplementation: String?
)

class KmpPlatformAwareBlockContractOverride(
    @JvmField val contractId: String,
    @JvmField val androidImplementation: String?,
    @JvmField val desktopImplementation: String?,
    @JvmField val fallbackImplementation: String?
)

class KmpExpectActualGeneratedFile(
    @JvmField val relativePath: String,
    @JvmField val content: String
)

class KmpExpectActualGenerationResult(
    @JvmField val files: List<KmpExpectActualGeneratedFile>
) {
    fun findFile(relativePath: String): KmpExpectActualGeneratedFile? {
        return files.firstOrNull { it.relativePath == relativePath }
    }
}

object KmpExpectActualGenerator {
    private const val GENERATED_OBJECT_NAME = "GeneratedPlatformBindings"

    private class StarterTemplate(
        val id: String,
        val functionName: String,
        val parameters: List<KmpPlatformAwareBlockParameter>,
        val returnType: String,
        val androidImplementation: String?,
        val desktopImplementation: String?,
        val fallbackImplementation: String?
    )

    private val STARTER_TEMPLATE_CATALOG = listOf(
        StarterTemplate(
            "platform_name",
            "platformName",
            emptyList(),
            "String",
            "\"Android\"",
            "\"Desktop\"",
            "\"Unsupported\""
        ),
        StarterTemplate(
            "logger_log",
            "logInfo",
            listOf(
                KmpPlatformAwareBlockParameter("tag", "String"),
                KmpPlatformAwareBlockParameter("message", "String")
            ),
            "Unit",
            "println(\"[${'$'}tag] ${'$'}message\")",
            "println(\"[${'$'}tag] ${'$'}message\")",
            "println(message)"
        ),
        StarterTemplate(
            "key_value_put",
            "putKeyValue",
            listOf(
                KmpPlatformAwareBlockParameter("key", "String"),
                KmpPlatformAwareBlockParameter("value", "String")
            ),
            "Unit",
            "run { System.setProperty(key, value); Unit }",
            "run { System.setProperty(key, value); Unit }",
            "Unit"
        ),
        StarterTemplate(
            "key_value_get",
            "getKeyValue",
            listOf(KmpPlatformAwareBlockParameter("key", "String")),
            "String",
            "System.getProperty(key).orEmpty()",
            "System.getProperty(key).orEmpty()",
            "\"\""
        ),
        StarterTemplate(
            "clock_now",
            "currentTimeMillis",
            emptyList(),
            "Long",
            "System.currentTimeMillis()",
            "System.currentTimeMillis()",
            "0L"
        )
    )

    @JvmStatic
    fun starterContracts(): List<KmpPlatformAwareBlockContract> {
        return starterContracts(emptyList())
    }

    @JvmStatic
    fun starterContracts(overrides: List<KmpPlatformAwareBlockContractOverride>): List<KmpPlatformAwareBlockContract> {
        val overridesById = linkedMapOf<String, KmpPlatformAwareBlockContractOverride>()
        for (override in overrides) {
            val normalizedId = override.contractId.trim()
            if (normalizedId.isNotEmpty()) {
                overridesById[normalizedId] = override
            }
        }

        return STARTER_TEMPLATE_CATALOG.map { template ->
            val override = overridesById[template.id]
            KmpPlatformAwareBlockContract(
                template.id,
                template.functionName,
                template.parameters,
                template.returnType,
                resolveTemplateImplementation(override?.androidImplementation, template.androidImplementation),
                resolveTemplateImplementation(override?.desktopImplementation, template.desktopImplementation),
                resolveTemplateImplementation(override?.fallbackImplementation, template.fallbackImplementation)
            )
        }
    }

    @JvmStatic
    fun generate(
        packageName: String,
        enabledTargets: List<KmpTarget>,
        contracts: List<KmpPlatformAwareBlockContract>
    ): KmpExpectActualGenerationResult {
        val normalizedPackage = packageName.trim().ifEmpty { "pro.sketchware.kmp" }
        val packagePath = normalizedPackage.replace('.', '/')
        val normalizedContracts = contracts.map { normalizeContract(it) }

        if (normalizedContracts.isEmpty()) {
            return KmpExpectActualGenerationResult(emptyList())
        }

        val files = mutableListOf<KmpExpectActualGeneratedFile>()
        files.add(
            KmpExpectActualGeneratedFile(
                "shared/src/commonMain/kotlin/$packagePath/$GENERATED_OBJECT_NAME.kt",
                buildExpectFileContent(normalizedPackage, normalizedContracts)
            )
        )

        val targetsBySourceSet = linkedMapOf<String, KmpTarget>()
        for (target in enabledTargets.distinct()) {
            if (!targetsBySourceSet.containsKey(target.sourceSetName)) {
                targetsBySourceSet[target.sourceSetName] = target
            }
        }

        for ((sourceSetName, target) in targetsBySourceSet) {
            files.add(
                KmpExpectActualGeneratedFile(
                    "shared/src/$sourceSetName/kotlin/$packagePath/$GENERATED_OBJECT_NAME.kt",
                    buildActualFileContent(normalizedPackage, target, normalizedContracts)
                )
            )
        }

        return KmpExpectActualGenerationResult(files)
    }

    private fun buildExpectFileContent(
        packageName: String,
        contracts: List<KmpPlatformAwareBlockContract>
    ): String {
        val content = StringBuilder()
        content.append("package ").append(packageName).append('\n').append('\n')
        content.append("expect object ").append(GENERATED_OBJECT_NAME).append(" {\n")
        for (contract in contracts) {
            content.append("    fun ")
                .append(contract.functionName)
                .append('(')
                .append(buildParameterSignature(contract.parameters))
                .append("): ")
                .append(contract.returnType)
                .append('\n')
        }
        content.append("}\n")
        return content.toString()
    }

    private fun buildActualFileContent(
        packageName: String,
        target: KmpTarget,
        contracts: List<KmpPlatformAwareBlockContract>
    ): String {
        val content = StringBuilder()
        content.append("package ").append(packageName).append('\n').append('\n')
        content.append("actual object ").append(GENERATED_OBJECT_NAME).append(" {\n")
        for (contract in contracts) {
            content.append("    actual fun ")
                .append(contract.functionName)
                .append('(')
                .append(buildParameterSignature(contract.parameters))
                .append("): ")
                .append(contract.returnType)
                .append(" = ")
                .append(resolveImplementation(contract, target))
                .append('\n')
        }
        content.append("}\n")
        return content.toString()
    }

    private fun buildParameterSignature(parameters: List<KmpPlatformAwareBlockParameter>): String {
        return parameters.mapIndexed { index, parameter ->
            val safeName = sanitizeIdentifier(parameter.name, "arg$index")
            val safeType = parameter.type.trim().ifEmpty { "Unit" }
            "$safeName: $safeType"
        }.joinToString(", ")
    }

    private fun resolveImplementation(contract: KmpPlatformAwareBlockContract, target: KmpTarget): String {
        val implementation = when (target) {
            KmpTarget.ANDROID -> contract.androidImplementation
            KmpTarget.DESKTOP -> contract.desktopImplementation
            else -> null
        }?.trim().orEmpty()

        if (implementation.isNotEmpty()) {
            return implementation
        }

        val fallback = contract.fallbackImplementation?.trim().orEmpty()
        if (fallback.isNotEmpty()) {
            return fallback
        }

        val contractId = contract.id.replace("\"", "\\\"")
        return "TODO(\"Missing actual implementation for contract '$contractId' on ${target.name}\")"
    }

    private fun normalizeContract(contract: KmpPlatformAwareBlockContract): KmpPlatformAwareBlockContract {
        val safeId = contract.id.trim().ifEmpty { "generated_contract" }
        val safeFunctionName = sanitizeIdentifier(contract.functionName, "generatedFunction")
        val safeReturnType = contract.returnType.trim().ifEmpty { "Unit" }
        return KmpPlatformAwareBlockContract(
            safeId,
            safeFunctionName,
            contract.parameters,
            safeReturnType,
            contract.androidImplementation,
            contract.desktopImplementation,
            contract.fallbackImplementation
        )
    }

    private fun sanitizeIdentifier(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return fallback
        }

        val normalized = trimmed.replace(Regex("[^A-Za-z0-9_]"), "_")
        if (normalized.isEmpty()) {
            return fallback
        }

        return if (normalized.first().isDigit()) "_$normalized" else normalized
    }

    private fun resolveTemplateImplementation(
        overrideImplementation: String?,
        defaultImplementation: String?
    ): String? {
        val normalizedOverride = overrideImplementation?.trim().orEmpty()
        if (normalizedOverride.isNotEmpty()) {
            return normalizedOverride
        }
        return defaultImplementation
    }
}
