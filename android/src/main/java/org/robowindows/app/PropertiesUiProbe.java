package org.robowindows.app;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;

/** Disposable Android view probes. Called only by the debug persistence test. */
final class PropertiesUiProbe {
    private PropertiesUiProbe() {}

    static void run(Context context, MachineStore store, MachineProfile initial) throws Exception {
        int[] closes = {0};
        MachinePropertiesView.Actions actions = new MachinePropertiesView.Actions() {
            @Override public void close() { closes[0]++; }
            @Override public void media(MachineProfile p, boolean utility) { throw new AssertionError("media action"); }
            @Override public void copy(MachineProfile p) { throw new AssertionError("copy action"); }
            @Override public void delete(MachineProfile p) { throw new AssertionError("delete action"); }
            @Override public void recover(MachineProfile p) { throw new AssertionError("recovery action"); }
        };
        MachinePropertiesView sheet = new MachinePropertiesView(context, store, initial, actions);
        edit(sheet).setText("Discard this draft");
        click(sheet, "CPU"); click(sheet, "General");
        require(edit(sheet).getText().toString().equals("Discard this draft"), "name survives tabs");
        click(sheet, "Cancel");
        require(closes[0] == 1 && current(store, initial.id).name.equals(initial.name), "Cancel leaves storage unchanged");

        sheet = new MachinePropertiesView(context, store, initial, actions);
        edit(sheet).setText("  Renamed Windows  ");
        click(sheet, "Apply");
        MachineProfile renamed = current(store, initial.id);
        require(renamed.name.equals("Renamed Windows") &&
                renamed.configurationGeneration == initial.configurationGeneration + 1 &&
                renamed.runtimePath.equals(initial.runtimePath) && renamed.mediaPath.equals(initial.mediaPath) &&
                renamed.selectedExecution.equals(initial.selectedExecution), "Apply renames only the chosen identity");
        require(!sheet.findViewWithTag("Apply").isEnabled() &&
                edit(sheet).getText().toString().equals(renamed.name), "Apply resets the normalized draft");
        edit(sheet).setText("Windows via OK"); click(sheet, "OK");
        require(closes[0] == 2 && current(store, initial.id).name.equals("Windows via OK"), "OK saves and closes");

        // App-local resource overrides: never mutate the user's Android font/display settings.
        for (float scale : new float[]{1f, 1.5f, 2f}) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.fontScale = scale;
            Context themed = new ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_RoboWindows);
            int width = ClassicUi.dp(themed, scale == 2f ? 1000 : 640);
            int height = ClassicUi.dp(themed, scale == 2f ? 600 : 360);
            MachineProfile p = current(store, initial.id);
            sheet = new MachinePropertiesView(themed, store, p, actions);
            for (String tab : new String[]{"General", "CPU", "Media", "Maintenance"}) {
                click(sheet, tab);
                sheet.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                sheet.layout(0, 0, width, height);
                checkLayout(sheet, themed);
                View footer = sheet.findViewWithTag("OK");
                require(footer.getHeight() >= ClassicUi.dp(themed, 48), "footer remains touchable");
            }
        }
        android.util.Log.i("RoboWindowsTest", "properties rename draft and enlarged-layout probes passed");
    }

    private static MachineProfile current(MachineStore store, String id) {
        for (MachineProfile p : store.load()) if (p.id.equals(id)) return p;
        throw new AssertionError("missing fixture profile");
    }
    private static EditText edit(MachinePropertiesView sheet) { return sheet.findViewWithTag("machine-name"); }
    private static void click(MachinePropertiesView sheet, String label) {
        View v = sheet.findViewWithTag(label);
        require(v != null && v.isEnabled() && v.performClick(), "available action " + label);
    }
    private static void checkLayout(View v, Context context) {
        if (v instanceof ScrollView) require(v.getHeight() >= ClassicUi.dp(context, 48), "usable scroll viewport");
        if (v instanceof Button) {
            Button b = (Button) v;
            require(b.getHeight() >= ClassicUi.dp(context, 48) && b.getWidth() >= ClassicUi.dp(context, 48), "button touch bounds");
            require(b.getLayout() != null && b.getLayout().getHeight() <=
                    b.getHeight() - b.getCompoundPaddingTop() - b.getCompoundPaddingBottom(), "button text not vertically clipped");
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) checkLayout(group.getChildAt(i), context);
        }
    }
    private static void require(boolean pass, String description) {
        if (!pass) throw new AssertionError(description);
    }
}
