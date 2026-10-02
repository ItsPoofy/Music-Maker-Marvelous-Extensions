package m3e.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.tile_entity.BlockEntities;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;

/**
 * Shows the useful contents of a Music Maker music box without changing its
 * block state, inventory rules, or sheet playback.  The state is extracted
 * from the synced block entity, so every client sees the same label.
 */
public final class MusicBoxContentsLabelRenderer
        implements BlockEntityRenderer<TileEntityMusicBox, MusicBoxContentsLabelRenderer.State> {

    private static final Vec3 LABEL_ANCHOR = new Vec3(0.5D, 1.18D, 0.5D);

    private MusicBoxContentsLabelRenderer() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        BlockEntityRendererRegistry.register(
                (net.minecraft.world.level.block.entity.BlockEntityType) BlockEntities.MUSIC_BOX,
                context -> new MusicBoxContentsLabelRenderer());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileEntityMusicBox musicBox, State state, float partialTick,
                                   Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(musicBox, state, partialTick, cameraPos, overlay);
        state.label = isHovered(musicBox) ? createLabel(musicBox) : null;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        if (state.label == null) return;

        collector.submitNameTag(poseStack, LABEL_ANCHOR, 0, state.label, true,
                LightCoordsUtil.FULL_BRIGHT, camera);
    }

    private static @Nullable Component createLabel(TileEntityMusicBox musicBox) {
        ItemStack sheet = musicBox.getSheetStack();
        String title = !sheet.isEmpty() && !ItemMusicSheet.isEmptySheet(sheet)
                ? sheet.getOrDefault(Items.SHEET_TITLE, "")
                : "";
        if (title.isBlank()) title = MusicBoxContentsLabelCache.titleAt(musicBox.getBlockPos());
        // Music Maker's compact music-box packet excludes the title, so use
        // the direct item component first and the synced title cache second.
        Component sheetName = title.isBlank() ? null : Component.literal(title);

        IItemInstrument instrument = musicBox.getInstrument();
        Component instrumentName = instrument instanceof Item item
                ? Component.literal(item.getName(new ItemStack(item)).getString())
                : null;

        if (sheetName == null) return instrumentName;
        if (instrumentName == null) return sheetName;
        return Component.empty().append(sheetName).append(Component.literal("  \u2022  ")).append(instrumentName);
    }

    private static boolean isHovered(TileEntityMusicBox musicBox) {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(musicBox.getBlockPos());
    }

    public static final class State extends BlockEntityRenderState {
        private @Nullable Component label;
    }
}
