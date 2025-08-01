package eu.rekawek.coffeegb.sound;

import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.memento.Memento;
import eu.rekawek.coffeegb.memento.Originator;

import java.io.Serializable;

public class FrequencySweep implements Serializable, Originator<FrequencySweep> {

    private static final int DIVIDER = Gameboy.TICKS_PER_SEC / 128;

    // sweep parameters
    private int period;

    private boolean negate;

    private int shift;

    // current process variables
    private int timer;

    private int shadowFreq;

    private int nr13, nr14;

    private int i;

    private boolean overflow;

    private boolean counterEnabled;

    private boolean negging;

    public void start() {
        counterEnabled = false;
        i = 8192;
    }

    public void trigger() {
        this.negging = false;
        this.overflow = false;

        this.shadowFreq = nr13 | ((nr14 & 0b111) << 8);
        this.timer = period == 0 ? 8 : period;
        this.counterEnabled = period != 0 || shift != 0;

        if (shift > 0) {
            calculate();
        }
    }

    public void setNr10(int value) {
        this.period = (value >> 4) & 0b111;
        this.negate = (value & (1 << 3)) != 0;
        this.shift = value & 0b111;
        if (negging && !negate) {
            overflow = true;
        }
    }

    public void setNr13(int value) {
        this.nr13 = value;
    }

    public void setNr14(int value) {
        this.nr14 = value;
        if ((value & (1 << 7)) != 0) {
            trigger();
        }
    }

    public int getNr13() {
        return nr13;
    }

    public int getNr14() {
        return nr14;
    }

    public void tick() {
        if (++i == DIVIDER) {
            i = 0;
            if (!counterEnabled) {
                return;
            }
            if (--timer == 0) {
                timer = period == 0 ? 8 : period;
                if (period != 0) {
                    int newFreq = calculate();
                    if (!overflow && shift != 0) {
                        shadowFreq = newFreq;
                        nr13 = shadowFreq & 0xff;
                        nr14 = (shadowFreq & 0x700) >> 8;
                        calculate();
                    }
                }
            }
        }
    }

    private int calculate() {
        int freq = shadowFreq >> shift;
        if (negate) {
            freq = shadowFreq - freq;
            negging = true;
        } else {
            freq = shadowFreq + freq;
        }
        if (freq > 2047) {
            overflow = true;
        }
        return freq;
    }

    public boolean isEnabled() {
        return !overflow;
    }

    @Override
    public Memento<FrequencySweep> saveToMemento() {
        return new FrequencySweepMemento(period, negate, shift, timer, shadowFreq, nr13, nr14, i, overflow, counterEnabled, negging);
    }

    @Override
    public void restoreFromMemento(Memento<FrequencySweep> memento) {
        if (!(memento instanceof FrequencySweepMemento mem)) {
            throw new IllegalArgumentException("Invalid memento type");
        }
        this.period = mem.period;
        this.negate = mem.negate;
        this.shift = mem.shift;
        this.timer = mem.timer;
        this.shadowFreq = mem.shadowFreq;
        this.nr13 = mem.nr13;
        this.nr14 = mem.nr14;
        this.i = mem.i;
        this.overflow = mem.overflow;
        this.counterEnabled = mem.counterEnabled;
        this.negging = mem.negging;
    }

    private record FrequencySweepMemento(int period, boolean negate, int shift, int timer, int shadowFreq, int nr13,
                                         int nr14, int i, boolean overflow, boolean counterEnabled,
                                         boolean negging) implements Memento<FrequencySweep> {
    }
}
