package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Geometry and matching dark casing for the Visual Piano's upward setup drawer. */
final class PianoExpandedMenu {
    static final int DESIGN_WIDTH = InstrumentPanelGeometry.FIXED_DRAWER_CANVAS_WIDTH;
    static final int DESIGN_HEIGHT = InstrumentPanelGeometry.FIXED_DRAWER_CANVAS_HEIGHT;
    private static final int INSET = 10;
    private static final int COLUMN_GAP = 10;
    private static final int TAB_WIDTH = 48;
    private static final int TAB_HEIGHT = 12;

    private PianoExpandedMenu() { }

    static Geometry geometry(PianoKeyboardGeometry.Layout piano, int screenWidth,
                             int screenHeight,
                             boolean expanded) {
        PianoKeyboardGeometry.Rect housing = piano.housing();
        int maximumHeight = Math.max(1,
                Math.min(screenHeight - 24, housing.y() - 16));
        int availableWidth = Math.max(1, screenWidth - 20);
        int compactWidth = PianoKeyboardGeometry.layout(screenWidth, screenHeight,
                PianoKeyboardGeometry.Mode.COMPACT).housing().width();
        int width = Math.min(Math.min(DESIGN_WIDTH, availableWidth), compactWidth);
        int height = Math.min(DESIGN_HEIGHT, maximumHeight);
        int panelBottom = housing.y() - 2;
        int panelY = panelBottom - height;
        // Use the viewport center, not the active piano housing. Both 61- and
        // 88-key modes are centered, but their odd/even widths can otherwise
        // move the drawer by a physical pixel when the mode changes.
        int panelX = (screenWidth - width) / 2;
        PianoKeyboardGeometry.Rect panel = new PianoKeyboardGeometry.Rect(
                panelX, panelY, width, height);
        int tabAnchor = expanded ? panel.y() : housing.y();
        int tabInset = 14;
        int scaledTabWidth = TAB_WIDTH;
        int scaledTabHeight = TAB_HEIGHT;
        PianoKeyboardGeometry.Rect tab = new PianoKeyboardGeometry.Rect(
                panel.x() + tabInset, tabAnchor - scaledTabHeight,
                Math.min(scaledTabWidth,
                        Math.max(1, panel.width() - tabInset * 2)), scaledTabHeight);

        return layout(panel, tab);
    }

    /** The same casing without a piano housing or expansion flap. */
    static Geometry standaloneGeometry(int screenWidth, int screenHeight) {
        int width = Math.min(DESIGN_WIDTH, Math.max(1, screenWidth - 20));
        int height = Math.min(DESIGN_HEIGHT, Math.max(1, screenHeight - 20));
        PianoKeyboardGeometry.Rect panel = new PianoKeyboardGeometry.Rect(
                (screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height);
        PianoKeyboardGeometry.Rect noTab = new PianoKeyboardGeometry.Rect(
                panel.x(), panel.y(), 0, 0);
        return layout(panel, noTab);
    }

    private static Geometry layout(PianoKeyboardGeometry.Rect panel,
                                   PianoKeyboardGeometry.Rect tab) {
        int interiorY = panel.y() + INSET;
        int interiorHeight = Math.max(1, panel.height() - INSET * 2);
        int navWidth = Math.clamp(panel.width() / 5, 104, 164);
        navWidth = Math.min(navWidth,
                Math.max(1, panel.width() - INSET * 2 - COLUMN_GAP - 80));
        PianoKeyboardGeometry.Rect navigation = new PianoKeyboardGeometry.Rect(
                panel.x() + INSET, interiorY, navWidth, interiorHeight);
        int contentX = navigation.right() + COLUMN_GAP;
        PianoKeyboardGeometry.Rect content = new PianoKeyboardGeometry.Rect(
                contentX, interiorY,
                Math.max(1, panel.right() - INSET - contentX), interiorHeight);
        return new Geometry(panel, tab, panel, navigation, content, 1.0F);
    }

    static final class Background extends AbstractWidget {
        private final Geometry geometry;

        Background(Geometry geometry) {
            super(geometry.canvas().x(), geometry.canvas().y(),
                    geometry.canvas().width(), geometry.canvas().height(),
                    Component.literal("Piano menu"));
            this.geometry = geometry;
            active = false;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                           int mouseX, int mouseY,
                                                           float partialTick) {
            PianoKeyboardGeometry.Rect panel = geometry.canvas();
            PianoKeyboardRenderer.drawPanelShadow(graphics, panel.x(), panel.y() + 3,
                    panel.width(), panel.height());
            PianoKeyboardRenderer.drawFourPixelPanel(graphics, panel.x(), panel.y(),
                    panel.width(), panel.height());
            PianoKeyboardGeometry.Rect navigation = geometry.navigation();
            PianoKeyboardGeometry.Rect content = geometry.content();
            PianoInsetPanel.draw(graphics, navigation.x(), navigation.y(),
                    navigation.width(), navigation.height());
            PianoInsetPanel.draw(graphics, content.x(), content.y(),
                    content.width(), content.height());
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    record Geometry(PianoKeyboardGeometry.Rect panel,
                    PianoKeyboardGeometry.Rect tab,
                    PianoKeyboardGeometry.Rect canvas,
                    PianoKeyboardGeometry.Rect navigation,
                    PianoKeyboardGeometry.Rect content,
                    float scale) { }

}
