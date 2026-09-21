package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Vibrator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import dev.aldi.sayuti.block.MyBlockDefaultsInstaller;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityBlocksManagerBinding;
import pro.sketchware.databinding.DialogBlockConfigurationBinding;
import pro.sketchware.databinding.DialogPaletteBinding;
import pro.sketchware.databinding.PalletCustomviewBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BlocksManager extends BaseAppCompatActivity {

    boolean isDialogShowing;
    View draggedView;
    private ArrayList<HashMap<String, Object>> all_blocks_list = new ArrayList<>();
    private String blocks_dir;
    private String pallet_dir;
    private int oldPos;
    private int newPos;
    private Activity activity;
    private ArrayList<HashMap<String, Object>> pallet_listmap = new ArrayList<>();
    private final ArrayList<HashMap<String, Object>> filtered_pallet_listmap = new ArrayList<>();
    private final ArrayList<Integer> filtered_palette_indices = new ArrayList<>();
    private String paletteSearchQuery = "";
    private ItemTouchHelper itemTouchHelper;
    private ActivityBlocksManagerBinding binding;
    private DialogPaletteBinding dialogBinding;
    private Vibrator vibrator;

    @Override
    public void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        binding = ActivityBlocksManagerBinding.inflate(getLayoutInflater());
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.background, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        initialize();
    }

    @Override
    public void onStop() {
        super.onStop();
        BlockLoader.refresh();
    }

    private void initialize() {
        activity = this;

        setSupportActionBar(binding.toolbar);

        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        binding.paletteRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.paletteRecycler.setAdapter(new PaletteAdapter(filtered_pallet_listmap));
        binding.fab.setOnClickListener(v -> showPaletteDialog(false, null, null, "#ffffff", null));

        readSettings();
        refreshList();
        recycleBin(binding.recycleBinCard);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (!paletteSearchQuery.isEmpty()) {
                    return false;
                }
                oldPos = viewHolder.getBindingAdapterPosition();
                newPos = target.getBindingAdapterPosition();

                Collections.swap(pallet_listmap, oldPos, newPos);

                Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemMoved(oldPos, newPos);
                swapRelatedBlocks(oldPos + 9, newPos + 9);

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int action) {
                if (action == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.setAlpha(0.7f);
                    draggedView = viewHolder.itemView;
                }
                super.onSelectedChanged(viewHolder, action);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                viewHolder.itemView.setAlpha(1f);
                FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
                FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    binding.background.setClipChildren(!isItNearTrash(draggedView, binding.recycleBin));
                    if (isItInTrash(draggedView, binding.recycleBin)) {
                        int pos = viewHolder.getBindingAdapterPosition();
                        binding.recycleBinCard.setAlpha(0.5f);
                        if (!isCurrentlyActive && pos != RecyclerView.NO_POSITION && pos < pallet_listmap.size() && !isDialogShowing) {
                            vibrator.vibrate(40L);
                            showMoveToBinDialog(pos);
                            isDialogShowing = true;
                        }
                        return;
                    }
                }
                binding.recycleBinCard.setAlpha(1f);
                isDialogShowing = false;
            }

        });

        itemTouchHelper.attachToRecyclerView(binding.paletteRecycler);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem searchItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Search");
        searchItem.setIcon(R.drawable.ic_mtrl_search);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        SearchView searchView = new SearchView(this);
        searchView.setQueryHint("Search by name, color or blocks");
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyPaletteSearchFilter(query, true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyPaletteSearchFilter(newText, true);
                return true;
            }
        });
        searchItem.setActionView(searchView);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                searchView.setQuery("", false);
                applyPaletteSearchFilter("", true);
                return true;
            }
        });
        if (!paletteSearchQuery.isEmpty()) {
            searchItem.expandActionView();
            searchView.setQuery(paletteSearchQuery, false);
            searchView.clearFocus();
        }

        menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Settings").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_settings)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Restore defaults");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        String title = Objects.requireNonNull(menuItem.getTitle()).toString();
        if (title.equals("Settings")) {
            showBlockConfigurationDialog();
        } else if (title.equals("Restore defaults")) {
            showRestoreDefaultsDialog();
        } else {
            return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void showRestoreDefaultsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore My Block defaults")
                .setMessage("This will replace current default My Block files with bundled defaults from My Block and My Block 2.")
                .setPositiveButton(Helper.getResString(R.string.common_word_yes), (dialog, which) -> {
                    MyBlockDefaultsInstaller.forceInstallDefaults(getApplicationContext());
                    ConfigActivity.setSetting(
                            ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                            ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH)
                    );
                    ConfigActivity.setSetting(
                            ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                            ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH)
                    );

                    BlockLoader.refresh();
                    readSettings();
                    refreshList();
                    refreshCount();
                    SketchwareUtil.toast("My Block defaults restored.");
                })
                .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        readSettings();
        refreshList();
        refreshCount();
    }

    private void showBlockConfigurationDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_folder_48dp);
        dialog.setTitle("Block configuration");

        DialogBlockConfigurationBinding dialogBinding = DialogBlockConfigurationBinding.inflate(getLayoutInflater());

        dialogBinding.palettesPath.setText(pallet_dir.replace(FileUtil.getExternalStorageDir(), ""));
        dialogBinding.blocksPath.setText(blocks_dir.replace(FileUtil.getExternalStorageDir(), ""));

        dialog.setView(dialogBinding.getRoot());

        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (view, which) -> {
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, Objects.requireNonNull(dialogBinding.palettesPath.getText()).toString());
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, Objects.requireNonNull(dialogBinding.blocksPath.getText()).toString());

            readSettings();
            refreshList();
            view.dismiss();
        });

        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);

        dialog.setNeutralButton("Defaults", (view, which) -> {
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));

            readSettings();
            refreshList();
            view.dismiss();
        });

        dialog.show();
    }

    private void showMoveToBinDialog(int position) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setIcon(R.drawable.ic_mtrl_delete);
        dialog.setTitle(R.string.block_move_to_bin);
        dialog.setMessage(R.string.common_message_confirm);
        dialog.setPositiveButton(R.string.common_word_yes, (v, which) -> {
            pallet_listmap.remove(position);
            Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemRemoved(position);
            Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemChanged(position);
            draggedView = null;
            moveRelatedBlocksToRecycleBin(position + 9);
            removeRelatedBlocks(position + 9);
            FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
            FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
            refreshCount();
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    private void readSettings() {
        pallet_dir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
        blocks_dir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));

        if (FileUtil.isExistFile(blocks_dir) && isValidJson(FileUtil.readFile(blocks_dir))) {
            try {
                all_blocks_list = getGson().fromJson(FileUtil.readFile(blocks_dir), Helper.TYPE_MAP_LIST);

                if (all_blocks_list != null) {
                    return;
                }
                // fall-through to shared handler
            } catch (JsonParseException e) {
                // fall-through to shared handler
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_dir), "Custom Blocks", v -> readSettings());
        }
    }

    private Boolean isValidJson(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() || element.isJsonArray();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private void refreshList() {
        parsePaletteJson:
        {
            String paletteJsonContent;
            if (FileUtil.isExistFile(pallet_dir) && !(paletteJsonContent = FileUtil.readFile(pallet_dir)).isEmpty()) {
                try {
                    pallet_listmap = getGson().fromJson(paletteJsonContent, Helper.TYPE_MAP_LIST);

                    if (pallet_listmap != null) {
                        break parsePaletteJson;
                    }
                    // fall-through to shared handler
                } catch (JsonParseException e) {
                    // fall-through to shared handler
                }

                SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_dir), "Custom Block Palettes", v -> refreshList());
            }
            pallet_listmap = new ArrayList<>();
        }

        applyPaletteSearchFilter(paletteSearchQuery, false);
        updatePaletteAdapterKeepingState();
        binding.recycleSub.setText("Blocks: " + (long) getN(-1));
        refreshCount();
    }

    private void applyPaletteSearchFilter(String query, boolean refreshRecycler) {
        paletteSearchQuery = query == null ? "" : query.trim();
        filtered_pallet_listmap.clear();
        filtered_palette_indices.clear();
        String normalizedQuery = normalizeSearchValue(paletteSearchQuery);

        if (normalizedQuery.isEmpty()) {
            for (int i = 0; i < pallet_listmap.size(); i++) {
                filtered_palette_indices.add(i);
                filtered_pallet_listmap.add(pallet_listmap.get(i));
            }
        } else {
            HashMap<Integer, Long> paletteBlockCounts = buildPaletteBlockCountLookup();
            for (int i = 0; i < pallet_listmap.size(); i++) {
                HashMap<String, Object> palette = pallet_listmap.get(i);
                if (paletteMatchesQuery(palette, i, normalizedQuery, paletteBlockCounts.getOrDefault(i + 9, 0L))) {
                    filtered_palette_indices.add(i);
                    filtered_pallet_listmap.add(palette);
                }
            }
        }

        if (refreshRecycler) {
            updatePaletteAdapterKeepingState();
            refreshCount();
        }
    }

    private HashMap<Integer, Long> buildPaletteBlockCountLookup() {
        HashMap<Integer, Long> blockCounts = new HashMap<>();
        if (all_blocks_list == null) {
            return blockCounts;
        }

        for (Map<String, Object> block : all_blocks_list) {
            Object paletteObj = block.get("palette");
            if (!(paletteObj instanceof String)) {
                continue;
            }

            try {
                int paletteValue = Integer.parseInt((String) paletteObj);
                if (paletteValue >= 9) {
                    blockCounts.put(paletteValue, blockCounts.getOrDefault(paletteValue, 0L) + 1L);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed palette values and keep searching with valid entries.
            }
        }

        return blockCounts;
    }

    private String normalizeSearchValue(String value) {
        if (value == null) {
            return "";
        }

        String lowered = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return lowered.replace("#", "");
    }

    private boolean paletteMatchesQuery(HashMap<String, Object> palette, int paletteIndex, String normalizedQuery, long blockCount) {
        Object name = palette.get("name");
        if (name instanceof String && normalizeSearchValue((String) name).contains(normalizedQuery)) {
            return true;
        }

        Object color = palette.get("color");
        if (color instanceof String && normalizeSearchValue((String) color).contains(normalizedQuery)) {
            return true;
        }

        String blockCountText = String.valueOf(blockCount);
        if (blockCountText.contains(normalizedQuery)) {
            return true;
        }

        String blocksLabel = normalizeSearchValue("blocks " + blockCountText);
        String paletteIndexLabel = String.valueOf(paletteIndex + 1);
        return blocksLabel.contains(normalizedQuery) || paletteIndexLabel.equals(normalizedQuery);
    }

    private CharSequence getHighlightedText(String text, String query) {
        if (text == null) {
            return "";
        }

        String normalizedQuery = normalizeSearchValue(query);
        if (normalizedQuery.isEmpty()) {
            return text;
        }

        int[] matchRange = findNormalizedMatchRange(text, normalizedQuery);
        if (matchRange == null) {
            return text;
        }

        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), matchRange[0], matchRange[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private int[] findNormalizedMatchRange(String source, String normalizedQuery) {
        StringBuilder normalizedSource = new StringBuilder();
        ArrayList<Integer> normalizedToOriginalIndex = new ArrayList<>();

        for (int i = 0; i < source.length(); i++) {
            char currentChar = source.charAt(i);
            String normalizedChar = normalizeSearchValue(String.valueOf(currentChar));
            if (normalizedChar.isEmpty()) {
                continue;
            }
            for (int j = 0; j < normalizedChar.length(); j++) {
                normalizedSource.append(normalizedChar.charAt(j));
                normalizedToOriginalIndex.add(i);
            }
        }

        int normalizedStart = normalizedSource.indexOf(normalizedQuery);
        if (normalizedStart < 0) {
            return null;
        }

        int normalizedEnd = normalizedStart + normalizedQuery.length() - 1;
        if (normalizedEnd >= normalizedToOriginalIndex.size()) {
            return null;
        }

        int start = normalizedToOriginalIndex.get(normalizedStart);
        int end = normalizedToOriginalIndex.get(normalizedEnd) + 1;
        return new int[]{start, end};
    }

    private void updatePaletteAdapterKeepingState() {
        Parcelable savedState = binding.paletteRecycler.getLayoutManager() != null
                ? binding.paletteRecycler.getLayoutManager().onSaveInstanceState()
                : null;
        binding.paletteRecycler.setAdapter(new PaletteAdapter(filtered_pallet_listmap));
        if (savedState != null && binding.paletteRecycler.getLayoutManager() != null) {
            binding.paletteRecycler.getLayoutManager().onRestoreInstanceState(savedState);
        }
    }

    private double getN(double _p) {
        int n = 0;
        if (all_blocks_list == null) return 0;

        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString().equals(String.valueOf((long) _p))) {
                n++;
            }
        }
        return n;
    }

    private void refreshCount() {
        if (filtered_pallet_listmap.isEmpty()) {
            binding.paletteCount.setText("No palettes");
        } else if (paletteSearchQuery.isEmpty()) {
            binding.paletteCount.setText(filtered_pallet_listmap.size() + " Palettes");
        } else {
            binding.paletteCount.setText(filtered_pallet_listmap.size() + " / " + pallet_listmap.size() + " Palettes");
        }
    }

    private void recycleBin(View view) {
        view.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), BlocksManagerDetailsActivity.class);
            intent.putExtra("position", "-1");
            intent.putExtra("dirB", blocks_dir);
            intent.putExtra("dirP", pallet_dir);
            startActivity(intent);
        });
        view.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Recycle bin")
                    .setMessage("Are you sure you want to empty the recycle bin? " +
                            "Blocks inside will be deleted PERMANENTLY, you CANNOT recover them!")
                    .setPositiveButton("Empty", (dialog, which) -> emptyRecyclebin())
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .show();
            return true;
        });
    }

    private void removeRelatedBlocks(double _p) {
        List<Map<String, Object>> newBlocks = new LinkedList<>();
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (!(Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p)) {
                if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) > _p) {
                    HashMap<String, Object> m = all_blocks_list.get(i);
                    m.put("palette", String.valueOf((long) (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) - 1)));
                    newBlocks.add(m);
                } else {
                    newBlocks.add(all_blocks_list.get(i));
                }
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(newBlocks));
        readSettings();
    }

    private void swapRelatedBlocks(double f, double s) {
        final String TEMP_PALETTE = "TEMP_SWAP";
        for (Map<String, Object> block : all_blocks_list) {
            Object paletteObj = block.get("palette");

            if (paletteObj == null) continue;
            double paletteValue;
            try {
                paletteValue = Double.parseDouble(paletteObj.toString());
            } catch (NumberFormatException e) {
                continue;
            }

            if (paletteValue == f) {
                block.put("palette", TEMP_PALETTE);
            } else if (paletteValue == s) {
                block.put("palette", String.valueOf((long) f));
            }
        }
        for (Map<String, Object> block : all_blocks_list) {
            if (TEMP_PALETTE.equals(block.get("palette"))) {
                block.put("palette", String.valueOf((long) s));
            }
        }
    }

    private void insertBlocksAt(double _p) {
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) > _p || Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p) {
                all_blocks_list.get(i).put("palette", String.valueOf((long) (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) + 1)));
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
        readSettings();
        refreshList();
    }

    private void moveRelatedBlocksToRecycleBin(double _p) {
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p) {
                all_blocks_list.get(i).put("palette", "-1");
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
        readSettings();
    }

    private void emptyRecyclebin() {
        List<Map<String, Object>> newBlocks = new LinkedList<>();
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (!(Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == -1)) {
                newBlocks.add(all_blocks_list.get(i));
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(newBlocks));
        readSettings();
        refreshList();
    }

    private void showPaletteDialog(boolean isEditing, Integer oldPosition, String oldName, String oldColor, Integer insertAtPosition) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.icon_style_white_96);
        dialog.setTitle(!isEditing ? "Create a new palette" : "Edit palette");

        dialogBinding = DialogPaletteBinding.inflate(getLayoutInflater());

        if (isEditing) {
            dialogBinding.nameEditText.setText(oldName);
            dialogBinding.colorEditText.setText(oldColor.replace("#", ""));
        }

        dialogBinding.openColorPalette.setOnClickListener(v1 -> {
            ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, 0xFFFFFFFF, false, false);
            colorPickerDialog.a(new ColorPickerDialog.b() {
                @Override
                public void a(int colorInt) {
                    dialogBinding.colorEditText.setText(String.format("%06X", colorInt & 0x00FFFFFF));
                }

                @Override
                public void a(String var1, int var2) {

                }
            });
            colorPickerDialog.showAtLocation(dialogBinding.openColorPalette, Gravity.CENTER, 0, 0);
        });

        dialog.setView(dialogBinding.getRoot());

        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            String nameInput = Objects.requireNonNull(dialogBinding.nameEditText.getText()).toString();
            String colorInput = Objects.requireNonNull(dialogBinding.colorEditText.getText()).toString();

            if (nameInput.isEmpty()) {
                SketchwareUtil.toast("Name cannot be empty", Toast.LENGTH_SHORT);
                return;
            }
            // add hash for the color 
            colorInput = "#" + colorInput;

            if (!PropertiesUtil.isHexColor(colorInput)) {
                SketchwareUtil.toast("Please enter a valid HEX color", Toast.LENGTH_SHORT);
                return;
            }

            if (PropertiesUtil.isHexColor(colorInput)) {
                Color.parseColor(colorInput);
                if (!isEditing) {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("name", nameInput);
                    map.put("color", colorInput);

                    if (insertAtPosition == null) {
                        pallet_listmap.add(map);
                    } else {
                        pallet_listmap.add(insertAtPosition, map);
                        insertBlocksAt(insertAtPosition + 9);
                    }

                    FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                    readSettings();
                    refreshList();
                } else {
                    pallet_listmap.get(oldPosition).put("name", nameInput);
                    pallet_listmap.get(oldPosition).put("color", colorInput);
                    FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                    readSettings();
                    refreshList();
                }
                refreshCount();
                v.dismiss();
            }
        });

        dialog.setNegativeButton(Helper.getResString(R.string.cancel), null);
        dialog.show();
    }


    private boolean isItInTrash(View draggedView, View trash) {
        if (draggedView == null) return false;

        int[] trashLocation = new int[2];
        trash.getLocationOnScreen(trashLocation);

        int[] draggedLocation = new int[2];
        draggedView.getLocationOnScreen(draggedLocation);

        int draggedY = draggedLocation[1];

        return draggedY <= trashLocation[1] + draggedView.getMeasuredHeight() / 2 && draggedY >= trashLocation[1] - draggedView.getMeasuredHeight() / 2;
    }

    private boolean isItNearTrash(View draggedView, View trash) {
        if (draggedView == null) return false;

        int[] trashLocation = new int[2];
        trash.getLocationOnScreen(trashLocation);

        int[] draggedLocation = new int[2];
        draggedView.getLocationOnScreen(draggedLocation);

        int draggedY = draggedLocation[1];

        return draggedY <= trashLocation[1] + draggedView.getMeasuredHeight() * 2 / 2 && draggedY >= trashLocation[1] - draggedView.getMeasuredHeight() * 2 / 2;
    }


    public class PaletteAdapter extends RecyclerView.Adapter<PaletteAdapter.ViewHolder> {

        private final ArrayList<HashMap<String, Object>> palettes;

        public PaletteAdapter(ArrayList<HashMap<String, Object>> palettes) {
            this.palettes = palettes;

        }

        @NonNull
        @Override
        public PaletteAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PalletCustomviewBinding itemBinding = PalletCustomviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new PaletteAdapter.ViewHolder(itemBinding);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull PaletteAdapter.ViewHolder holder, int position) {
            String paletteColorValue = (String) palettes.get(position).get("color");
            assert paletteColorValue != null;
            int backgroundColor = PropertiesUtil.parseColor(paletteColorValue);
            int absolutePaletteIndex = filtered_palette_indices.get(position);
            HashMap<String, Object> currentPalette = palettes.get(position);
            String paletteName = Objects.requireNonNull(currentPalette.get("name")).toString();
            String blockCountText = String.valueOf((long) getN(absolutePaletteIndex + 9));
            String subtitleText = "Blocks: " + blockCountText + " | " + paletteColorValue;

            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemBinding.title.setText(getHighlightedText(paletteName, paletteSearchQuery));
            holder.itemBinding.sub.setText(getHighlightedText(subtitleText, paletteSearchQuery));
            holder.itemBinding.color.setBackgroundColor(backgroundColor);
            holder.itemBinding.dragHandler.setVisibility(paletteSearchQuery.isEmpty() ? View.VISIBLE : View.GONE);
            binding.recycleSub.setText("Blocks: " + (long) getN(-1));

            holder.itemBinding.backgroundCard.setOnLongClickListener(v -> {
                final String edit = "Edit";
                final String delete = "Delete";
                final String insert = "Insert";

                PopupMenu popup = new PopupMenu(BlocksManager.this, holder.itemBinding.color);
                Menu menu = popup.getMenu();
                menu.add(edit);
                menu.add(delete);
                menu.add(insert);
                popup.setOnMenuItemClickListener(item -> {
                    int adapterPos = holder.getAbsoluteAdapterPosition();
                    if (adapterPos == RecyclerView.NO_POSITION || adapterPos >= filtered_palette_indices.size()) {
                        return false;
                    }
                    int pos = filtered_palette_indices.get(adapterPos);
                    switch (Objects.requireNonNull(item.getTitle()).toString()) {
                        case edit:
                            showPaletteDialog(true, pos,
                                    Objects.requireNonNull(pallet_listmap.get(pos).get("name")).toString(),
                                    Objects.requireNonNull(pallet_listmap.get(pos).get("color")).toString(), null);
                            break;

                        case delete:
                            new MaterialAlertDialogBuilder(BlocksManager.this)
                                    .setTitle(Objects.requireNonNull(pallet_listmap.get(pos).get("name")).toString())
                                    .setMessage("Remove all blocks related to this palette?")
                                    .setPositiveButton("Remove permanently", (dialog, which) -> {
                                        pallet_listmap.remove(pos);
                                        FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                                        removeRelatedBlocks(pos + 9);
                                        readSettings();
                                        applyPaletteSearchFilter(paletteSearchQuery, false);
                                        updatePaletteAdapterKeepingState();
                                        refreshCount();
                                    })
                                    .setNegativeButton(R.string.common_word_cancel, null)
                                    .setNeutralButton(R.string.block_move_to_bin, (dialog, which) -> {
                                        moveRelatedBlocksToRecycleBin(pos + 9);
                                        pallet_listmap.remove(pos);
                                        FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                                        removeRelatedBlocks(pos + 9);
                                        readSettings();
                                        applyPaletteSearchFilter(paletteSearchQuery, false);
                                        updatePaletteAdapterKeepingState();
                                        refreshCount();
                                    }).show();
                            break;

                        case insert:
                            showPaletteDialog(false, null, null, null, pos);
                            break;

                        default:
                    }
                    return true;
                });
                popup.show();

                return true;
            });

            holder.itemBinding.dragHandler.setOnTouchListener((v, event) -> {
                if (!paletteSearchQuery.isEmpty()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(holder);
                }

                return false;
            });

            holder.itemBinding.backgroundCard.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), BlocksManagerDetailsActivity.class);
                int adapterPos = holder.getAbsoluteAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION || adapterPos >= filtered_palette_indices.size()) {
                    return;
                }
                int pos = filtered_palette_indices.get(adapterPos);
                intent.putExtra("position", String.valueOf((long) (pos + 9)));
                intent.putExtra("dirB", blocks_dir);
                intent.putExtra("dirP", pallet_dir);
                startActivity(intent);
            });

        }

        @Override
        public int getItemCount() {
            return palettes.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public PalletCustomviewBinding itemBinding;

            public ViewHolder(PalletCustomviewBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }
        }
    }
}

