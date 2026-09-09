package org.robowindows.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.io.IOException;

/** Windows-style property sheet; changing widgets only changes the draft. */
final class MachinePropertiesView extends LinearLayout {
    interface Actions {
        void close();
        void media(MachineProfile profile, boolean utility);
        void copy(MachineProfile profile);
        void delete(MachineProfile profile);
        void recover(MachineProfile profile);
    }
    private final MachineStore store;
    private final Actions actions;
    private MachineProfile profile;
    private SettingsDraft draft;
    private int tab;
    private String status = "Changes take effect only when you choose Apply or OK.";
    private static final String[] TABS = {"General", "CPU", "Media", "Maintenance"};

    MachinePropertiesView(Context context, MachineStore store, MachineProfile profile, Actions actions) {
        super(context);
        this.store = store; this.actions = actions;
        reset(profile);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        setBackgroundColor(ClassicUi.GRAY);
        render();
    }

    private int dp(int n) { return ClassicUi.dp(getContext(), n); }
    private void reset(MachineProfile p) {
        profile = p;
        draft = new SettingsDraft(p.name, p.memoryMb, p.fixedCycles, p.soundEnabled,
                p.isDynamicSelected(), p.cpuCore);
    }
    private TextView text(String s) {
        TextView v = new TextView(getContext());
        v.setText(s); v.setTextColor(ClassicUi.INK); v.setTextSize(16);
        v.setPadding(dp(8), dp(10), dp(8), dp(10));
        return v;
    }
    private Button button(String s, Runnable run) {
        Button b = ClassicUi.button(getContext(), s, v -> run.run());
        b.setTag(s.replace("✓ ", ""));
        return b;
    }
    private void option(LinearLayout body, String label, boolean selected, boolean enabled, Runnable action) {
        Button b = button((selected ? "✓ " : "") + label, action);
        b.setSelected(selected); b.setEnabled(enabled);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8); body.addView(b, p);
    }
    private void render() {
        View focused = findFocus();
        Object focusTag = focused == null ? null : focused.getTag();
        removeAllViews();
        addView(ClassicUi.title(getContext(), profile.name + " — Properties", this::requestClose));
        LinearLayout tabs = new LinearLayout(getContext());
        for (int i = 0; i < TABS.length; i++) {
            final int selected = i;
            Button b = button(TABS[i], () -> { tab = selected; render(); });
            b.setSelected(tab == i);
            tabs.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        addView(tabs);
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(12));
        body.setBackground(ClassicUi.bevel(getContext(), true));
        boolean editable = !store.hasInterruptedSession() && !store.requiresDynamicMediaCheck(profile);
        if (!editable) body.addView(text("Settings are read-only until the session and disk-check recovery finish."));
        if (tab == 0) {
            TextView nameLabel = text("Machine name");
            EditText name = new EditText(getContext());
            name.setId(View.generateViewId());
            nameLabel.setLabelFor(name.getId());
            name.setTag("machine-name");
            name.setTextSize(16); name.setTextColor(ClassicUi.INK);
            name.setInputType(InputType.TYPE_CLASS_TEXT);
            name.setSingleLine(true);
            name.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            name.setBackground(ClassicUi.bevel(getContext(), true));
            name.setPadding(dp(12), dp(8), dp(12), dp(8));
            name.setMinimumHeight(dp(48)); name.setEnabled(editable);
            name.setText(draft.name);
            name.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    draft.name = s.toString(); updateFooter();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            body.addView(nameLabel); body.addView(name);
            body.addView(text(profile.family + " · " + (profile.isExperimental() ? "Experimental copy" : "Stable machine") +
                    " · " + draft.memoryMb + " MB memory"));
            option(body, "DOS compatibility · 16 MB", draft.memoryMb == 16, editable,
                    () -> { draft.memoryMb = 16; draft.normalCore = "normal"; render(); });
            option(body, "Windows compatible · 64 MB", draft.memoryMb == 64, editable,
                    () -> { draft.memoryMb = 64; draft.normalCore = "normal"; render(); });
            CheckBox sound = new CheckBox(getContext());
            sound.setText("Sound enabled"); sound.setTextSize(16); sound.setTextColor(ClassicUi.INK);
            sound.setMinHeight(dp(48)); sound.setChecked(draft.sound); sound.setEnabled(editable);
            sound.setOnCheckedChangeListener((b, checked) -> { draft.sound = checked; updateFooter(); });
            body.addView(sound);
        } else if (tab == 1) {
            String reason = store.dynamicUnavailable(profile, CpuFixtureGate.passed(getContext()));
            option(body, "Normal", !draft.dynamic, editable, () -> { draft.dynamic = false; render(); });
            if (BuildConfig.DEBUG && profile.isExperimental()) {
                option(body, "DynRec (experimental) · fixed 20k", draft.dynamic, editable && reason == null,
                        () -> { draft.dynamic = true; render(); });
            }
            body.addView(text(reason == null ? "DynRec uses an isolated trial. Clean shutdown retains your choice; " +
                    "a failed trial returns to Normal and requires disk recovery." : reason));
            if (profile.isExperimental()) {
                body.addView(text("Saved Normal cycles: " + draft.normalCycles / 1000 + "k" +
                        (draft.dynamic ? " — select Normal to edit. DynRec always uses 20k." : "")));
                for (int cycles : MachineStore.EXPERIMENTAL_CYCLE_CANDIDATES) {
                    option(body, cycles / 1000 + "k cycles" + (cycles == 30000 ? " · may break audio" : ""),
                            draft.normalCycles == cycles, editable && !draft.dynamic,
                            () -> { draft.normalCycles = cycles; render(); });
                }
            }
        } else if (tab == 2) {
            body.addView(text("Imported media: " + profile.mediaName));
            if (store.isWindowsInstaller(profile)) {
                boolean installer = store.bootsInstaller(profile);
                body.addView(text("Boot source: " + (store.selectedUtility(profile) != null ? "Utility disk" :
                        installer ? "Windows installer" : "Windows disk")));
                option(body, installer ? "Use Windows disk" : "Use installer", false, editable,
                        () -> resolveDraft(() -> actions.media(profile, false)));
                option(body, "Choose utility disk…", false, editable && !draft.dynamic,
                        () -> resolveDraft(() -> actions.media(profile, true)));
                if (draft.dynamic) body.addView(text("Select and apply Normal before choosing utility media."));
            } else body.addView(text("Boots the imported machine media."));
            body.addView(text("Media actions update the stopped machine only; they do not boot it."));
        } else {
            if (store.requiresDynamicMediaCheck(profile)) {
                body.addView(text("This copy needs a Normal disk-check recovery boot. Only shutdown inside Windows clears quarantine."));
                option(body, "Start disk-check recovery…", false, !store.hasInterruptedSession(),
                        () -> resolveDraft(() -> actions.recover(profile)));
            } else if (!profile.isExperimental()) {
                body.addView(text("Create a separate writable copy before performance experiments."));
                option(body, "Create experimental copy…", false, editable,
                        () -> resolveDraft(() -> actions.copy(profile)));
            } else body.addView(text("Experimental copy. Stable machine disks are not used by its trials."));
            option(body, "Delete machine…", false, editable,
                    () -> resolveDraft(() -> actions.delete(profile)));
        }
        scroll.addView(body);
        addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        statusView = text(status);
        addView(statusView);
        LinearLayout footer = new LinearLayout(getContext());
        footer.setGravity(android.view.Gravity.END);
        footer.addView(button("OK", () -> { if (save()) actions.close(); }), new LinearLayout.LayoutParams(0, -2, 1));
        footer.addView(button("Cancel", actions::close), new LinearLayout.LayoutParams(0, -2, 1));
        apply = button("Apply", () -> { if (save()) render(); });
        footer.addView(apply, new LinearLayout.LayoutParams(0, -2, 1));
        addView(footer);
        updateFooter();
        if (focusTag != null) {
            View target = findViewWithTag(focusTag);
            if (target != null && target.isEnabled()) target.requestFocus();
        }
    }
    private Button apply;
    private TextView statusView;
    private void updateFooter() {
        apply.setEnabled(draft.dirty());
        statusView.setText(draft.dirty() ? "Unsaved changes" : status);
    }
    private boolean save() {
        if (!draft.dirty()) return true;
        try {
            reset(store.saveSettings(profile, draft, CpuFixtureGate.passed(getContext())));
            status = "Settings saved. Use Start in Machines when ready.";
            return true;
        } catch (IOException error) {
            new AlertDialog.Builder(getContext()).setTitle("Settings not saved")
                    .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
            return false;
        }
    }
    void requestClose() {
        if (!draft.dirty()) { actions.close(); return; }
        new AlertDialog.Builder(getContext()).setTitle("Discard changes?")
                .setMessage("Unapplied settings will be discarded.")
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Discard", (d, w) -> actions.close()).show();
    }
    private void resolveDraft(Runnable action) {
        if (!draft.dirty()) { action.run(); return; }
        new AlertDialog.Builder(getContext()).setTitle("Unsaved settings")
                .setMessage("Apply or discard your changes before continuing.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Discard", (d, w) -> { reset(profile); render(); action.run(); })
                .setPositiveButton("Apply", (d, w) -> { if (save()) { render(); action.run(); } }).show();
    }
}
