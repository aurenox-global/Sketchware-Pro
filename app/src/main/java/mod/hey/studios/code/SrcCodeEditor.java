package mod.hey.studios.code;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import a.a.a.Lx;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.lang.diagnostic.Quickfix;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import mod.jbk.code.JavaDiagnosticsAnalyzer;
import mod.jbk.code.ProjectDartLanguage;
import mod.jbk.code.ProjectJavaLanguage;
import mod.jbk.code.ProjectKotlinLanguage;
import pro.sketchware.lsp.LocalSymbolNavigationProvider;
import pro.sketchware.lsp.LspLanguage;
import pro.sketchware.lsp.LspNavigationCoordinator;
import pro.sketchware.lsp.LspNavigationLocation;
import pro.sketchware.lsp.LspNavigationRequest;
import pro.sketchware.lsp.LspNavigationResult;
import pro.sketchware.lsp.LspSessionConfig;
import pro.sketchware.lsp.ProjectSymbolNavigationProvider;
import pro.sketchware.R;
import pro.sketchware.activities.ai.LocalAiManagerActivity;
import pro.sketchware.ai.LocalAiConfig;
import pro.sketchware.ai.LocalAiPromptFactory;
import pro.sketchware.ai.LocalAiService;
import pro.sketchware.ai.rag.LocalAiRagRegistry;
import pro.sketchware.ai.rag.LocalAiSemanticContext;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.CodeEditorHsBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;

public class SrcCodeEditor extends BaseAppCompatActivity {
    public static final String FLAG_FROM_ANDROID_MANIFEST = "from_android_manifest";
    /** Extra con la linea a la que saltar al abrir (0-based), usada por la navegacion de codigo. */
    public static final String EXTRA_GOTO_LINE = "goto_line";
    public static final List<Pair<String, Class<? extends EditorColorScheme>>> KNOWN_COLOR_SCHEMES = List.of(
            new Pair<>("Default", EditorColorScheme.class),
            new Pair<>("GitHub", SchemeGitHub.class),
            new Pair<>("Eclipse", SchemeEclipse.class),
            new Pair<>("Darcula", SchemeDarcula.class),
            new Pair<>("VS2019", SchemeVS2019.class),
            new Pair<>("NotepadXX", SchemeNotepadXX.class)
    );
    public static SharedPreferences pref;
    public static int languageId;
    /** sc_id del proyecto abierto: permite ofrecer los simbolos del proyecto al autocompletar. */
    private static String currentScId;
    /** Retardo antes de analizar el fichero, para no compilar en cada tecla. */
    private static final int DIAGNOSTICS_DELAY_MS = 1200;
    private static final ExecutorService diagnosticsExecutor = Executors.newSingleThreadExecutor();
    private final Handler diagnosticsHandler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean diagnosticsRunning = new java.util.concurrent.atomic.AtomicBoolean(false);
    private Runnable pendingDiagnostics;
    /** Ultima posicion del cursor (linea 0-based y columna), para la navegacion de codigo. */
    private int caretLine;
    private int caretColumn;
    private String beforeContent = "";
    private CodeEditorHsBinding binding;
    private boolean fromAndroidManifest;
    private String scId;
    private String activityName;

    public static void loadCESettings(Context c, CodeEditor ed, String prefix) {
        loadCESettings(c, ed, prefix, false);
    }

    public static void loadCESettings(Context c, CodeEditor ed, String prefix, boolean loadTheme) {
        pref = c.getSharedPreferences("hsce", Activity.MODE_PRIVATE);

        int text_size = pref.getInt(prefix + "_ts", 12);
        int theme = pref.getInt(prefix + "_theme", 3);
        boolean word_wrap = pref.getBoolean(prefix + "_ww", false);
        boolean auto_c = pref.getBoolean(prefix + "_ac", true);
        boolean auto_complete_symbol_pairs = pref.getBoolean(prefix + "_acsp", true);

        if (loadTheme) selectTheme(ed, theme);
        ed.setTextSize(text_size);
        ed.setWordwrap(word_wrap);
        ed.getProps().symbolPairAutoCompletion = auto_complete_symbol_pairs;
        ed.getComponent(EditorAutoCompletion.class).setEnabled(auto_c);
    }

    public static void selectTheme(CodeEditor ed, int which) {
        if (!(ed.getColorScheme() instanceof TextMateColorScheme)) {
            EditorColorScheme scheme = switch (which) {
                case 1 -> new SchemeGitHub();
                case 2 -> new SchemeEclipse();
                case 3 -> new SchemeDarcula();
                case 4 -> new SchemeVS2019();
                case 5 -> new SchemeNotepadXX();
                default -> new EditorColorScheme();
            };

            ed.setColorScheme(scheme);
        }
    }

    public static void selectLanguage(CodeEditor ed, int which) {
        switch (which) {
            default:
            case 0:
                ed.setEditorLanguage(new ProjectJavaLanguage(currentScId));
                languageId = 0;
                break;

            case 1:
                ed.setEditorLanguage(new ProjectKotlinLanguage(currentScId));
                languageId = 1;
                break;

            case 2:
                ed.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML));
                languageId = 2;
                break;

            case 3:
                ed.setEditorLanguage(new ProjectDartLanguage(currentScId));
                languageId = 3;
                break;
        }

    }

    public static String prettifyXml(String xml, int indentAmount, Intent extras) {
        if (xml == null || xml.trim().isEmpty()) return xml;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
            document.normalize();

            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate(
                    "//text()[normalize-space()='']", document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); ++i) {
                Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
                    String.valueOf(indentAmount));

            boolean omitXmlDecl = extras != null && extras.hasExtra("disableHeader");
            if (omitXmlDecl) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            String result = writer.toString();

            if (!omitXmlDecl && result.startsWith("<?xml")) {
                int endOfDecl = result.indexOf("?>");
                if (endOfDecl != -1 && endOfDecl + 2 < result.length()
                        && result.charAt(endOfDecl + 2) != '\n') {
                    result = result.substring(0, endOfDecl + 2) + "\n"
                            + result.substring(endOfDecl + 2);
                }
            }

            String[] lines = result.split("\n");
            StringBuilder formatted = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("<") && !trimmed.startsWith("<?")
                        && !trimmed.startsWith("<!") && trimmed.contains(" ")
                        && !trimmed.startsWith("</")) {

                    int indentBase = line.indexOf('<');
                    String baseIndent = " ".repeat(Math.max(0, indentBase));
                    String attrIndent = baseIndent + "    "; // 4-space attribute indent

                    boolean selfClosing = trimmed.endsWith("/>");
                    int tagEnd = trimmed.indexOf(' ');

                    if (tagEnd > 0) {
                        String tagName = trimmed.substring(1, tagEnd);
                        String attrPart = trimmed.substring(tagEnd + 1)
                                .replaceAll("/?>$", "").trim();
                        String[] attrs = attrPart.split("\\s+(?=[^=]+\\=)");

                        formatted.append(baseIndent).append("<").append(tagName).append("\n");
                        for (String attr : attrs) {
                            formatted.append(attrIndent).append(attr.trim()).append("\n");
                        }

                        int lastNewline = formatted.lastIndexOf("\n");
                        if (lastNewline != -1) {
                            formatted.delete(lastNewline, formatted.length());
                        }

                        formatted.append(selfClosing ? " />" : ">").append("\n");
                    } else {
                        formatted.append(line).append("\n");
                    }
                } else {
                    formatted.append(line).append("\n");
                }
            }

            return formatted.toString().trim();

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Adds a specified amount of tabs.
     */
    public static void a(StringBuilder code, int tabAmount) {
        for (int i = 0; i < tabAmount; ++i) {
            code.append('\t');
        }
    }

    public static void showSwitchThemeDialog(Activity activity, CodeEditor codeEditor, DialogInterface.OnClickListener listener) {
        EditorColorScheme currentScheme = codeEditor.getColorScheme();
        var knownColorSchemesProperlyOrdered = new ArrayList<>(KNOWN_COLOR_SCHEMES);
        Collections.reverse(knownColorSchemesProperlyOrdered);
        int selectedThemeIndex = knownColorSchemesProperlyOrdered.stream()
                .filter(pair -> pair.second.equals(currentScheme.getClass()))
                .map(KNOWN_COLOR_SCHEMES::indexOf)
                .findFirst()
                .orElse(-1);
        String[] themeItems = KNOWN_COLOR_SCHEMES.stream()
                .map(pair -> pair.first)
                .toArray(String[]::new);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themeItems, selectedThemeIndex, listener)
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    public static void showSwitchLanguageDialog(Activity activity, CodeEditor codeEditor, DialogInterface.OnClickListener listener) {
        CharSequence[] languagesList = {
                "Java",
                "Kotlin",
                "XML",
                "Dart"
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Language")
                .setSingleChoiceItems(languagesList, languageId, listener)
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = CodeEditorHsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fromAndroidManifest = getIntent().getBooleanExtra(FLAG_FROM_ANDROID_MANIFEST, false);
        String title = getIntent().getStringExtra("title");
        scId = getIntent().getStringExtra("sc_id");
        currentScId = scId;
        activityName = getIntent().getStringExtra("activity_name");

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(16);

        if (fromAndroidManifest) {
            String filePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
            if (FileUtil.isExistFile(filePath)) {
                ArrayList<HashMap<String, Object>> arrayList = getGson()
                        .fromJson(FileUtil.readFile(filePath), Helper.TYPE_MAP_LIST);
                for (int i = 0; i < arrayList.size(); i++) {
                    if (arrayList.get(i).get("name").equals(activityName)) {
                        beforeContent = (String) arrayList.get(i).get("value");
                    }
                }
            }
        }

        if (!fromAndroidManifest)
            beforeContent = FileUtil.readFile(getIntent().getStringExtra("content"));
        binding.editor.setText(beforeContent);

        // Si venimos de "ir a definicion" o "buscar usos", colocamos el cursor en la linea pedida.
        int gotoLine = getIntent().getIntExtra(EXTRA_GOTO_LINE, -1);
        if (gotoLine >= 0) {
            final int targetLine = gotoLine;
            binding.editor.postDelayed(() -> {
                int lineCount = binding.editor.getText().getLineCount();
                if (lineCount > 0) {
                    binding.editor.setSelection(Math.min(targetLine, lineCount - 1), 0);
                    binding.editor.ensureSelectionVisible();
                }
            }, 300);
        }

        if (title.endsWith(".java")) {
            binding.editor.setEditorLanguage(new ProjectJavaLanguage(currentScId));
            languageId = 0;
            setupLiveDiagnostics(binding.editor, title);
        } else if (title.endsWith(".kt")) {
            binding.editor.setEditorLanguage(new ProjectKotlinLanguage(currentScId));
            binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
            languageId = 1;
        } else if (title.endsWith(".xml")) {
            binding.editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML));
            if (ThemeUtils.isDarkThemeEnabled(getApplicationContext())) {
                binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
            } else {
                binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_GITHUB));
            }
            languageId = 2;
        } else if (title.endsWith(".dart")) {
            // Fase 7: Dart/Flutter usa TextMate propio (source.dart) con el tema Dracula, igual que Kotlin.
            binding.editor.setEditorLanguage(new ProjectDartLanguage(currentScId));
            binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
            languageId = 3;
        }

        loadCESettings(this, binding.editor, "act", true);
        loadToolbar();

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    public void save() {
        beforeContent = binding.editor.getText().toString();

        if (fromAndroidManifest) {
            String filePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
            if (FileUtil.isExistFile(filePath)) {
                ArrayList<HashMap<String, Object>> activitiesComponents = getGson()
                        .fromJson(FileUtil.readFile(filePath), Helper.TYPE_MAP_LIST);
                for (int i = 0; i < activitiesComponents.size(); i++) {
                    if (activitiesComponents.get(i).get("name").equals(activityName)) {
                        activitiesComponents.get(i).put("value", beforeContent);
                        FileUtil.writeFile(filePath, getGson().toJson(activitiesComponents));
                        SketchwareUtil.toast("Saved");
                        return;
                    }
                }
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", activityName);
                map.put("value", beforeContent);
                activitiesComponents.add(map);
                FileUtil.writeFile(filePath, getGson().toJson(activitiesComponents));
            } else {
                ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", activityName);
                map.put("value", beforeContent);
                arrayList.add(map);
                FileUtil.writeFile(filePath, getGson().toJson(arrayList));
            }
        } else FileUtil.writeFile(getIntent().getStringExtra("content"), beforeContent);

        SketchwareUtil.toast("Saved");
    }

    @Override
    public void onBackPressed() {
        if (beforeContent.equals(binding.editor.getText().toString())) {
            super.onBackPressed();
        } else {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setIcon(R.drawable.ic_warning_96dp);
            dialog.setTitle(Helper.getResString(R.string.common_word_warning));
            dialog.setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message));

            dialog.setPositiveButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
                v.dismiss();
                finish();
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
            dialog.show();
        }
    }

    private void loadToolbar() {
        {
            String title = getIntent().getStringExtra("title");
            binding.toolbar.setTitle(title);
            SharedPreferences local_pref = getSharedPreferences("hsce", Activity.MODE_PRIVATE);
            Menu toolbarMenu = binding.toolbar.getMenu();
            toolbarMenu.clear();
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Undo").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_undo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Redo").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_redo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Save").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            if (isFileInLayoutFolder() && getIntent().hasExtra("sc_id")) {
                toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Layout Preview");
            }
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "AI: Explain code");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "AI: Fix code");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "AI: Generate from comment");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "AI Settings");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Find & Replace");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Word wrap").setCheckable(true).setChecked(local_pref.getBoolean("act_ww", false));
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Pretty print");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Select language");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Select theme");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Auto complete").setCheckable(true).setChecked(local_pref.getBoolean("act_ac", true));
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Auto complete symbol pair").setCheckable(true).setChecked(local_pref.getBoolean("act_acsp", true));
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Go to definition");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Find usages");

            binding.toolbar.setOnMenuItemClickListener(item -> {
                String title1 = item.getTitle().toString();
                switch (title1) {
                    case "Undo":
                        binding.editor.undo();
                        break;

                    case "Redo":
                        binding.editor.redo();
                        break;

                    case "Save":
                        save();
                        break;

                    case "Go to definition":
                        navigateToSymbol(true);
                        break;

                    case "Find usages":
                        navigateToSymbol(false);
                        break;

                    case "Pretty print":
                        if (getIntent().hasExtra("java")) {
                            StringBuilder b = new StringBuilder();

                            for (String line : binding.editor.getText().toString().split("\n")) {
                                String trims = (line + "X").trim();
                                trims = trims.substring(0, trims.length() - 1);

                                b.append(trims);
                                b.append("\n");
                            }

                            boolean err = false;
                            String ss = b.toString();

                            try {
                                ss = Lx.j(ss, true);
                            } catch (Exception e) {
                                err = true;
                                SketchwareUtil.toastError("Your code contains incorrectly nested parentheses");
                            }

                            if (!err) binding.editor.setText(ss);

                        } else if (getIntent().hasExtra("xml")) {
                            String format = prettifyXml(binding.editor.getText().toString(), 4, getIntent());

                            if (format != null) {
                                binding.editor.setText(format);
                            } else {
                                SketchwareUtil.toastError("Failed to format XML file", Toast.LENGTH_LONG);
                            }
                        } else {
                            SketchwareUtil.toast("Only Java and XML files can be formatted");
                        }
                        break;

                    case "Select language":
                        showSwitchLanguageDialog(this, binding.editor, (dialog, which) -> {
                            selectLanguage(binding.editor, which);
                            dialog.dismiss();
                        });
                        break;

                    case "Find & Replace":
                        binding.editor.getSearcher().stopSearch();
                        binding.editor.beginSearchMode();
                        break;

                    case "Select theme":
                        showSwitchThemeDialog(this, binding.editor, (dialog, which) -> {
                            selectTheme(binding.editor, which);
                            pref.edit().putInt("act_theme", which).apply();
                            dialog.dismiss();
                        });
                        break;

                    case "Word wrap":
                        item.setChecked(!item.isChecked());
                        binding.editor.setWordwrap(item.isChecked());

                        pref.edit().putBoolean("act_ww", item.isChecked()).apply();
                        break;

                    case "Auto complete symbol pair":
                        item.setChecked(!item.isChecked());
                        binding.editor.getProps().symbolPairAutoCompletion = item.isChecked();

                        pref.edit().putBoolean("act_acsp", item.isChecked()).apply();
                        break;

                    case "Auto complete":
                        item.setChecked(!item.isChecked());

                        binding.editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                        pref.edit().putBoolean("act_ac", item.isChecked()).apply();
                        break;

                    case "Layout Preview":
                        toLayoutPreview();
                        break;

                    case "AI: Explain code":
                        runLocalAiAction(LocalAiPromptFactory.Action.EXPLAIN_CODE);
                        break;

                    case "AI: Fix code":
                        runLocalAiAction(LocalAiPromptFactory.Action.FIX_CODE);
                        break;

                    case "AI: Generate from comment":
                        runLocalAiAction(LocalAiPromptFactory.Action.GENERATE_FROM_COMMENT);
                        break;

                    case "AI Settings":
                        startActivity(new Intent(getApplicationContext(), LocalAiManagerActivity.class));
                        break;

                    default:
                        return false;
                }
                return true;
            });
        }
    }

    private void runLocalAiAction(LocalAiPromptFactory.Action action) {
        String filename = getIntent().getStringExtra("title");
        String language = getLanguageLabel(filename);
        String editorContent = binding.editor.getText().toString();
        LocalAiConfig config = LocalAiConfig.load(getApplicationContext());

        var progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Local AI")
                .setMessage("Preparing context...")
                .setNegativeButton("Cancel", (dialog, which) -> LocalAiService.getInstance().cancel())
                .create();
        progressDialog.show();

        Thread promptBuilder = new Thread(() -> {
            String prompt = buildPromptForAction(action, filename, language, editorContent, config);
            runOnUiThread(() -> startLocalAiGeneration(action, prompt, progressDialog));
        }, "local-ai-prompt-builder");
        promptBuilder.start();
    }

    private String buildPromptForAction(LocalAiPromptFactory.Action action,
                                        String filename,
                                        String language,
                                        String editorContent,
                                        LocalAiConfig config) {
        int maxContextChars = LocalAiPromptFactory.maxContextCharsFor(config.getContextSize(), config.getMaxTokens());
        try {
            LocalAiSemanticContext semanticContext = buildSemanticContextForPrompt(action, filename, editorContent);
            return LocalAiPromptFactory.build(
                    action,
                    filename,
                    language,
                    editorContent,
                    getLocalAiRole(action),
                    semanticContext,
                    maxContextChars
            );
        } catch (Throwable throwable) {
            return LocalAiPromptFactory.build(
                    action,
                    filename,
                    language,
                    editorContent,
                    getLocalAiRole(action),
                    LocalAiSemanticContext.EMPTY,
                    maxContextChars
            );
        }
    }

    private void startLocalAiGeneration(LocalAiPromptFactory.Action action, String prompt, androidx.appcompat.app.AlertDialog progressDialog) {
        LocalAiService.getInstance().generate(this, prompt, new LocalAiService.Callback() {
            @Override
            public void onStarted() {
                if (!progressDialog.isShowing()) {
                    progressDialog.show();
                }
            }

            @Override
            public void onStatus(String status) {
                progressDialog.setMessage(status);
            }

            @Override
            public void onSuccess(String response) {
                showLocalAiResult(action, response);
            }

            @Override
            public void onError(Throwable throwable) {
                SketchwareUtil.showAnErrorOccurredDialog(SrcCodeEditor.this, throwable.getMessage());
            }

            @Override
            public void onFinished() {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        });
    }

    private LocalAiSemanticContext buildSemanticContextForPrompt(LocalAiPromptFactory.Action action,
                                                                 String filename,
                                                                 String content) {
        if (scId == null || scId.isEmpty()) {
            return LocalAiSemanticContext.EMPTY;
        }

        String query = buildSemanticQuery(action, filename, content);
        if (query.isEmpty()) {
            return LocalAiSemanticContext.EMPTY;
        }

        String currentPath = getIntent().getStringExtra("content");
        try {
            return LocalAiRagRegistry.getInstance()
                    .semanticIndexer()
                    .buildContext(scId, currentPath, query);
        } catch (Throwable ignored) {
            return LocalAiSemanticContext.EMPTY;
        }
    }

    private String buildSemanticQuery(LocalAiPromptFactory.Action action, String filename, String content) {
        String actionHint = switch (action) {
            case EXPLAIN_CODE -> "explain architecture and behavior";
            case FIX_CODE -> "fix error stacktrace bug issue";
            case GENERATE_FROM_COMMENT -> "generate missing implementation from TODO";
        };

        String safeFile = filename == null ? "" : filename;
        String safeContent = content == null ? "" : content;
        String snippet = safeContent.length() > 700 ? safeContent.substring(0, 700) : safeContent;
        snippet = snippet.replaceAll("\\s+", " ").trim();
        return (actionHint + " " + safeFile + " " + snippet).trim();
    }

    private LocalAiPromptFactory.Role getLocalAiRole(LocalAiPromptFactory.Action action) {
        return switch (action) {
            case EXPLAIN_CODE -> LocalAiPromptFactory.Role.SKETCHWARE_ARCHITECT;
            case FIX_CODE -> LocalAiPromptFactory.Role.BUILD_DEBUGGER;
            case GENERATE_FROM_COMMENT -> LocalAiPromptFactory.Role.JAVA_KOTLIN_ENGINEER;
        };
    }

    private String getLanguageLabel(String filename) {
        if (filename == null) {
            return "java";
        } else if (filename.endsWith(".kt")) {
            return "kotlin";
        } else if (filename.endsWith(".xml")) {
            return "xml";
        } else if (filename.endsWith(".dart")) {
            return "dart";
        }
        return "java";
    }

    private void showLocalAiResult(LocalAiPromptFactory.Action action, String response) {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView resultView = new android.widget.TextView(this);
        int padding = SketchwareUtil.dpToPx(20);
        resultView.setPadding(padding, padding, padding, padding);
        resultView.setText(response == null || response.trim().isEmpty() ? "The model returned an empty response." : response);
        resultView.setTextIsSelectable(true);
        scrollView.addView(resultView);

        var builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Local AI result")
                .setView(scrollView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setNeutralButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("AI response", response == null ? "" : response);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(SrcCodeEditor.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                });

        if (action == LocalAiPromptFactory.Action.FIX_CODE) {
            builder.setPositiveButton("Replace editor", (dialog, which) -> binding.editor.setText(resultView.getText().toString()));
        } else if (action == LocalAiPromptFactory.Action.GENERATE_FROM_COMMENT) {
            builder.setPositiveButton("Append", (dialog, which) ->
                    binding.editor.setText(binding.editor.getText().toString() + "\n" + resultView.getText()));
        }

        builder.show();
    }

    @Override
    public void onStop() {
        super.onStop();

        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        pref.edit().putInt("act_ts", (int) (binding.editor.getTextSizePx() / scaledDensity)).apply();
    }

    private boolean isFileInLayoutFolder() {
        String content = getIntent().getStringExtra("content");
        if (content != null) {
            File file = new File(content);
            if (content.contains("/resource/layout/")) {
                String layoutFolder = file.getParent();
                return layoutFolder != null && layoutFolder.endsWith("/resource/layout");
            }
        }
        return false;
    }

    private void toLayoutPreview() {
        Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("xml", binding.editor.getText().toString());
        startActivity(intent);
    }

    // ------------------------------------------------------------------ IDE: diagnosticos en vivo

    /**
     * Analiza con ECJ, con retardo, el fichero Java abierto y subraya errores y avisos en el editor.
     * El analisis corre fuera del hilo de UI y nunca puede romper el editor: si algo falla, simplemente
     * no se marca nada.
     */
    private void setupLiveDiagnostics(CodeEditor editor, String fileName) {
        editor.subscribeEvent(ContentChangeEvent.class, (event, source) -> scheduleDiagnostics(editor, fileName));
        editor.subscribeEvent(SelectionChangeEvent.class, (event, source) -> {
            CharPosition position = event.getLeft();
            if (position != null) {
                caretLine = position.line;
                caretColumn = position.column;
            }
        });
        scheduleDiagnostics(editor, fileName);

        // Precalienta los indices de clases del SDK y de las librerias para que la primera
        // sugerencia no tarde (la primera lectura de android.jar cuesta unas decimas de segundo).
        String scId = currentScId;
        if (scId != null && !scId.isEmpty()) {
            Context appContext = getApplicationContext();
            diagnosticsExecutor.execute(() -> {
                mod.jbk.code.SdkSymbolIndex.getSdkClasses(appContext);
                mod.jbk.code.SdkSymbolIndex.getLibraryClasses(appContext, scId);
            });
        }
    }

    private void scheduleDiagnostics(CodeEditor editor, String fileName) {
        if (pendingDiagnostics != null) {
            diagnosticsHandler.removeCallbacks(pendingDiagnostics);
        }
        pendingDiagnostics = () -> runDiagnostics(editor, fileName);
        diagnosticsHandler.postDelayed(pendingDiagnostics, DIAGNOSTICS_DELAY_MS);
    }

    private void runDiagnostics(CodeEditor editor, String fileName) {
        String scId = currentScId;
        if (scId == null || scId.isEmpty()) {
            return;
        }
        // Si ya hay un analisis en curso, no encolamos otro: reintentamos cuando toque.
        if (diagnosticsRunning.get()) {
            scheduleDiagnostics(editor, fileName);
            return;
        }
        diagnosticsRunning.set(true);
        String content = editor.getText().toString();
        Context appContext = getApplicationContext();
        diagnosticsExecutor.execute(() -> {
            try {
                List<JavaDiagnosticsAnalyzer.Problem> problems =
                        JavaDiagnosticsAnalyzer.analyze(appContext, scId, fileName, content);
                diagnosticsHandler.post(() -> applyDiagnostics(editor, content, problems));
            } finally {
                diagnosticsRunning.set(false);
            }
        });
    }

    private void applyDiagnostics(CodeEditor editor, String analyzedContent,
                                  List<JavaDiagnosticsAnalyzer.Problem> problems) {
        // Si el usuario ha seguido escribiendo, este analisis ya no vale: hay otro en camino.
        if (!analyzedContent.contentEquals(editor.getText().toString())) {
            return;
        }

        DiagnosticsContainer container = new DiagnosticsContainer();
        for (JavaDiagnosticsAnalyzer.Problem problem : problems) {
            int start = indexOfLine(analyzedContent, problem.line);
            if (start < 0) {
                continue;
            }
            int end = analyzedContent.indexOf('\n', start);
            if (end < 0) {
                end = analyzedContent.length();
            }
            end = Math.max(end, start + 1);

            DiagnosticRegion region = new DiagnosticRegion(start, end, (short) problem.severity);
            region.detail = new DiagnosticDetail(problem.message, problem.message,
                    buildQuickfixes(editor, problem), null);
            container.addDiagnostic(region);
        }
        editor.setDiagnostics(container);
    }

    /**
     * Acciones rapidas de un diagnostico. De momento, cuando ECJ no resuelve un tipo, ofrecemos
     * importar la clase que lo resolveria (segun el indice del SDK y de las librerias).
     */
    private static List<Quickfix> buildQuickfixes(CodeEditor editor, JavaDiagnosticsAnalyzer.Problem problem) {
        if (problem.importCandidates == null || problem.importCandidates.isEmpty()) {
            return null;
        }
        long documentVersion = editor.getText().getDocumentVersion();
        List<Quickfix> quickfixes = new ArrayList<>();
        for (String candidate : problem.importCandidates) {
            quickfixes.add(new Quickfix("Importar " + candidate, documentVersion,
                    () -> insertImport(editor, candidate)));
        }
        return quickfixes;
    }

    /** Inserta {@code import x;} despues de la linea del paquete (o al principio si no hay paquete). */
    private static void insertImport(CodeEditor editor, String fullName) {
        String content = editor.getText().toString();
        String[] lines = content.split("\n", -1);

        int insertLine = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("package ")) {
                insertLine = i + 1;
                break;
            }
            if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                    && !trimmed.startsWith("*")) {
                insertLine = i;
                break;
            }
        }

        Content text = editor.getText();
        text.beginBatchEdit();
        try {
            text.insert(insertLine, 0, "import " + fullName + ";\n");
        } finally {
            text.endBatchEdit();
        }
    }

    /** Indice del primer caracter de una linea (1-based), o -1 si esa linea no existe. */
    private static int indexOfLine(String text, int line) {
        if (line <= 1) {
            return 0;
        }
        int index = 0;
        for (int current = 1; current < line; current++) {
            int next = text.indexOf('\n', index);
            if (next < 0) {
                return -1;
            }
            index = next + 1;
        }
        return index;
    }

    // ------------------------------------------------------------------ IDE: navegacion de codigo

    /**
     * Resuelve el simbolo bajo el cursor con el indice del proyecto (con el buscador local del
     * fichero como reserva) y abre el resultado: la definicion o los usos.
     */
    private void navigateToSymbol(boolean definition) {
        String scId = currentScId;
        String filePath = getIntent().getStringExtra("content");
        if (scId == null || scId.isEmpty() || filePath == null || filePath.isEmpty()) {
            android.widget.Toast.makeText(this, "Sin proyecto abierto", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String content = binding.editor.getText().toString();
        LspSessionConfig config = new LspSessionConfig(scId, filePath, LspLanguage.JAVA);
        LspNavigationRequest request = new LspNavigationRequest(content, null, caretLine, caretColumn);
        LspNavigationCoordinator coordinator = new LspNavigationCoordinator(
                new ProjectSymbolNavigationProvider(),
                new LocalSymbolNavigationProvider(),
                8000);

        android.widget.Toast.makeText(this, definition ? "Buscando definicion..." : "Buscando usos...",
                android.widget.Toast.LENGTH_SHORT).show();

        diagnosticsExecutor.execute(() -> {
            LspNavigationResult result = definition
                    ? coordinator.requestDefinition(config, request)
                    : coordinator.requestReferences(config, request);
            coordinator.dispose();
            diagnosticsHandler.post(() -> showNavigationResult(result, definition));
        });
    }

    private void showNavigationResult(LspNavigationResult result, boolean definition) {
        List<LspNavigationLocation> locations = (result == null || result.locations == null)
                ? Collections.emptyList() : result.locations;

        if (locations.isEmpty()) {
            android.widget.Toast.makeText(this,
                    definition ? "No se ha encontrado la definicion" : "No se han encontrado usos",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (locations.size() == 1) {
            openLocation(locations.get(0));
            return;
        }

        String[] entries = new String[locations.size()];
        for (int i = 0; i < locations.size(); i++) {
            LspNavigationLocation location = locations.get(i);
            entries[i] = new java.io.File(location.documentPath).getName()
                    + ":" + (location.range.startLine + 1)
                    + "\n" + location.preview;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(definition ? "Definiciones" : "Usos (" + locations.size() + ")")
                .setItems(entries, (dialog, which) -> openLocation(locations.get(which)))
                .show();
    }

    /** Abre otro fichero del proyecto en el editor, colocando el cursor en la linea indicada. */
    private void openLocation(LspNavigationLocation location) {
        if (location == null || location.documentPath == null || location.documentPath.isEmpty()) {
            return;
        }
        Intent intent = new Intent(getApplicationContext(), SrcCodeEditor.class);
        intent.putExtra("title", new java.io.File(location.documentPath).getName());
        intent.putExtra("content", location.documentPath);
        if (currentScId != null) {
            intent.putExtra("sc_id", currentScId);
        }
        intent.putExtra(EXTRA_GOTO_LINE, location.range.startLine);
        startActivity(intent);
    }
}