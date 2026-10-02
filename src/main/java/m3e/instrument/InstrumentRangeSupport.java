package m3e.instrument;

import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.ItemInstrument;
import xerca.xercamusic.common.item.Items;

/** Identifies sampled melodic instruments that can safely use their endpoint samples. */
public final class InstrumentRangeSupport {
    private InstrumentRangeSupport() { }

    public static boolean supportsExtension(IItemInstrument instrument) {
        return instrument instanceof ItemInstrument
                && instrument != Items.DRUM
                && instrument != Items.CYMBAL
                && instrument != Items.DRUM_KIT
                && !M3EInstruments.isRedstoneDrumKit(instrument)
                && !M3EInstruments.isDexed(instrument);
    }
}
