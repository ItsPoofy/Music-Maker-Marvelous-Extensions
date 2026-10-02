package m3e.client;

import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;
import m3e.instrument.InstrumentRangeState;
import m3e.instrument.InstrumentRangeSupport;

/** Client preference for extending sampled melodic instruments from their edge samples. */
public final class InstrumentRangeOverride {
    private InstrumentRangeOverride() { }

    public static boolean enabledFor(IItemInstrument instrument) {
        return InstrumentRangeState.extendsRange(instrument);
    }

    public static boolean supportsExtension(int instrumentId) {
        return instrumentId >= 0 && instrumentId < Items.INSTRUMENTS.size()
                && supportsExtension(Items.INSTRUMENTS.get(instrumentId));
    }

    public static boolean supportsExtension(IItemInstrument instrument) {
        return InstrumentRangeSupport.supportsExtension(instrument);
    }
}
