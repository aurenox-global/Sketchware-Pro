package pro.sketchware.ai;

import pro.sketchware.ai.rag.LocalAiSemanticContext;

public class LocalAiPromptFactory {
    private static final int MAX_CONTEXT_CHARS = 12000;

    public enum Action {
        EXPLAIN_CODE,
        FIX_CODE,
        GENERATE_FROM_COMMENT
    }

    public enum Role {
        AGENT_MAESTRO("Agente Maestro Sketchware Pro",
            "Experto total en el ecosistema Sketchware Pro: estructura de proyectos, pantallas y actividades; bloques, eventos, listas, mapas, SharedPreferences, intents, timers, Firebase; diseno UI Material; Java/Kotlin del generador; compilacion y librerias locales. Resuelve cualquier peticion devolviendo las acciones JSON del agente (add_view, add_event, inject_code); si la peticion es solo informativa, responde con reply breve en espanol."),
        SKETCHWARE_ARCHITECT("Arquitecto Sketchware Pro",
            "Domina la estructura de proyectos Sketchware Pro: pantallas (xml), actividades (java), eventos, variables, recursos, permisos y flujo de compilacion. Para crear o modificar pantallas devuelve acciones JSON (add_view + inject_code); usa screen=main y parent=root salvo que el usuario pida otra pantalla."),
        BLOCK_LOGIC_ENGINEER("Ingeniero de bloques y logica",
                "Traduce requisitos a logica ejecutable: eventos (initializeLogic, onClick), variables, listas, mapas, SharedPreferences, intents, timers y Firebase. Devuelve inject_code con codigo Java minimo y valido para el evento indicado (initializeLogic o el id de la vista)."),
        UI_UX_DESIGNER("Disenador UI Android/Material",
                "Disena pantallas con jerarquia clara y componentes Material (linear, button, text, input, card, image, scroll). Usa ids secuenciales por tipo (button1, text1, input1...), parent=root por defecto y dimensiones match_parent/wrap_content; nunca inventes ids duplicados."),
        JAVA_KOTLIN_ENGINEER("Programador Java/Kotlin Android",
            "Escribe codigo Android valido para el generador de Sketchware: sentencias sueltas dentro de initializeLogic o de un onClick (sin declarar metodos ahi), findViewById(R.id.button1) o binding.button1, lifecycle, adapters, WebView, intents y APIs nativas. Codigo corto y listo para inyectar."),
        CROSS_PLATFORM_ENGINEER("Arquitecto Flutter/React Native",
            "Disena apps multiplataforma Flutter/React Native con arquitectura por pantallas, estado, navegacion y capa de datos. Si el usuario pide una app visual editable en Sketchware, responde con acciones JSON de vistas en lugar de codigo multiplataforma."),
        BUILD_DEBUGGER("Debugger de compilacion Sketchware",
                "Diagnostica errores de Java/Kotlin/XML/manifest/Gradle/D8/R8, recursos duplicados, imports ambiguos, dependencias y empaquetado. Da parches concretos: archivo y evento donde inyectar codigo o el codigo corregido completo."),
        LOCAL_LIB_NATIVE_ENGINEER("Experto en librerias locales y nativas",
                "Integra JAR/AAR/DEX/res/assets/jniLibs, ProGuard, JNI y .so por ABI dentro de Sketchware Pro. Explica pasos exactos y, cuando sea posible, devuelve el codigo de integracion listo para pegar.");

        private final String title;
        private final String skills;

        Role(String title, String skills) {
            this.title = title;
            this.skills = skills;
        }

        public String getTitle() {
            return title;
        }

        public String getSkills() {
            return skills;
        }

        public String getPromptPrefix() {
            return "Rol: " + title + "\nSkills Sketchware Pro: " + skills + "\n"
                    + "Responde directo, en espanol, con pasos aplicables dentro de Sketchware Pro. "
                    + "Cuando propongas codigo, indica donde pegarlo o que evento/bloque lo activa.\n\n";
        }
    }

    public static String build(Action action, String filename, String language, String content) {
        Role role = switch (action) {
            case EXPLAIN_CODE -> Role.SKETCHWARE_ARCHITECT;
            case FIX_CODE -> Role.BUILD_DEBUGGER;
            case GENERATE_FROM_COMMENT -> Role.JAVA_KOTLIN_ENGINEER;
        };
        return build(action, filename, language, content, role, LocalAiSemanticContext.EMPTY);
    }

    public static String build(Action action, String filename, String language, String content, Role role) {
        return build(action, filename, language, content, role, LocalAiSemanticContext.EMPTY);
    }

    public static String build(Action action,
                               String filename,
                               String language,
                               String content,
                               Role role,
                               LocalAiSemanticContext semanticContext) {
        return build(action, filename, language, content, role, semanticContext, MAX_CONTEXT_CHARS);
    }

    public static String build(Action action,
                               String filename,
                               String language,
                               String content,
                               Role role,
                               LocalAiSemanticContext semanticContext,
                               int maxContextChars) {
        String safeContent = trimContext(content == null ? "" : content, maxContextChars);
        String fileLabel = filename == null || filename.isEmpty() ? "current file" : filename;
        String languageLabel = language == null || language.isEmpty() ? "code" : language;
        Role safeRole = role == null ? Role.SKETCHWARE_ARCHITECT : role;
        LocalAiSemanticContext safeContext = semanticContext == null ? LocalAiSemanticContext.EMPTY : semanticContext;
        String ragSection = safeContext.toPromptSection();
        String ragInstructions = ragSection.isEmpty()
                ? ""
                : "Use the retrieved context when relevant, but prioritize correctness of the current file.\n\n"
                        + ragSection
                        + "\n";

        return switch (action) {
            case EXPLAIN_CODE -> safeRole.getPromptPrefix()
                    + "Explain this " + languageLabel + " file clearly in Spanish. Focus on what it does, risky parts, and practical improvements.\n\n"
                    + ragInstructions
                    + "File: " + fileLabel + "\n\n```" + languageLabel + "\n" + safeContent + "\n```";
            case FIX_CODE -> safeRole.getPromptPrefix()
                    + "Find likely bugs in this " + languageLabel + " code and return a corrected version when possible. "
                    + "Keep the same behavior and style. If you cannot safely rewrite the whole file, return focused patches and explain why in Spanish.\n\n"
                    + ragInstructions
                    + "File: " + fileLabel + "\n\n```" + languageLabel + "\n" + safeContent + "\n```";
            case GENERATE_FROM_COMMENT -> safeRole.getPromptPrefix()
                    + "Read this " + languageLabel + " file and generate the missing code implied by the latest TODO/comment or incomplete area. "
                    + "Return only useful code first, then a short Spanish note if needed.\n\n"
                    + ragInstructions
                    + "File: " + fileLabel + "\n\n```" + languageLabel + "\n" + safeContent + "\n```";
        };
    }

    public static int maxContextCharsFor(int contextSize, int maxTokens) {
        int reservedForOutput = Math.max(maxTokens + 96, 512);
        int tokensForContent = Math.max(256, contextSize - reservedForOutput);
        return tokensForContent * 3;
    }

    public static final String NO_REASONING_INSTRUCTION =
            "IMPORTANTE: NO razones ni muestres pensamiento paso a paso. No incluyas bloques <think> ni explicaciones de tu proceso. Responde directo y conciso.";

    private static final String AGENT_JSON_SCHEMA =
            "IMPORTANTE: \"type\" de cada action es SOLO add_view, add_event o inject_code; el tipo de vista va en \"view_type\".\n"
                    + "Acciones:\n"
                    + "  add_view: crear vista. Campos: screen,parent,view_type,id,text,text_size,hint,width,height,orientation,margenes,background_color. view_type: linear|vertical|horizontal|scroll|card|button|text|input|image|webview|progress|list|spinner|checkbox|switch|seekbar\n"
                    + "  inject_code: inyectar codigo Java. Campos: target,event,code. event=\"initializeLogic\" o el id de la vista para su onClick\n"
                    + "  add_event: crear el onClick de una vista. Campos: target,view_id,event,code\n"
                    + "Reglas:\n"
                    + "  - Crea primero las vistas (add_view) y despues sus eventos (inject_code/add_event), SIEMPRE juntos en el mismo JSON.\n"
                    + "  - ids unicos: button1, text1, input1...\n"
                    + "  - screen=main, parent=root.\n"
                    + "  - codigo Java dentro del evento indicado, sin declarar metodos.\n"
                    + "  - Para preguntas o consejos usa solo \"reply\" con \"actions\": [].\n"
                    + "Ejemplo exacto:\n"
                    + "{\"reply\":\"Listo, boton creado con su evento.\",\"actions\":["
                    + "{\"type\":\"add_view\",\"screen\":\"main\",\"parent\":\"root\",\"view_type\":\"button\",\"id\":\"button1\",\"text\":\"Presioname\",\"width\":\"match_parent\",\"height\":\"wrap_content\",\"margin_top\":8},"
                    + "{\"type\":\"inject_code\",\"target\":\"main\",\"event\":\"button1\",\"code\":\"Toast.makeText(getApplicationContext(), \\\"Hola\\\", Toast.LENGTH_SHORT).show();\"}]}\n";

    public static String buildAgentPrompt(String projectScope,
                                          String userPrompt,
                                          String projectContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres un Agente de Sketchware Pro. Si el usuario pide crear o modificar algo, ")
                .append("responde SOLO con un JSON valido con \"reply\" (1-2 frases en espanol) y \"actions\".\n\n")
                .append(AGENT_JSON_SCHEMA)
                .append("\nContexto del proyecto:\n")
                .append(projectContext == null || projectContext.trim().isEmpty()
                        ? "- Scope: " + (projectScope == null ? "" : projectScope) + "\n"
                        : projectContext)
                .append("\nSolicitud del usuario:\n")
                .append(userPrompt);
        return prompt.toString();
    }

    public static String buildAssistantPrompt(String userPrompt, boolean reasoningEnabled) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Actua como asistente breve y preciso. ")
                .append("Responde solo lo que se te pregunta, sin relleno ni explicaciones largas. ")
                .append("No repitas frases ni ideas. ")
                .append("Si no piden detalle, responde en un parrafo corto o lista corta.");
        if (!reasoningEnabled) {
            prompt.append('\n').append(NO_REASONING_INSTRUCTION);
        }
        prompt.append("\n\n").append(userPrompt);
        return prompt.toString();
    }

    private static String trimContext(String content, int maxContextChars) {
        int safeMax = Math.max(2000, maxContextChars);
        if (content.length() <= safeMax) {
            return content;
        }
        int half = safeMax / 2;
        return content.substring(0, half)
                + "\n\n/* ...content trimmed for local model context... */\n\n"
                + content.substring(content.length() - half);
    }
}