package android.icu.impl.coll;

import android.icu.impl.Normalizer2Impl;
import android.icu.text.UCharacterIterator;

public final class FCDIterCollationIterator extends IterCollationIterator {
    static final /* synthetic */ boolean -assertionsDisabled = (!FCDIterCollationIterator.class.desiredAssertionStatus());
    private int limit;
    private final Normalizer2Impl nfcImpl;
    private StringBuilder normalized;
    private int pos;
    private StringBuilder s;
    private int start;
    private State state = State.ITER_CHECK_FWD;

    private enum State {
        ITER_CHECK_FWD,
        ITER_CHECK_BWD,
        ITER_IN_FCD_SEGMENT,
        IN_NORM_ITER_AT_LIMIT,
        IN_NORM_ITER_AT_START
    }

    public FCDIterCollationIterator(CollationData data, boolean numeric, UCharacterIterator ui, int startIndex) {
        super(data, numeric, ui);
        this.start = startIndex;
        this.nfcImpl = data.nfcImpl;
    }

    public void resetToOffset(int newOffset) {
        super.resetToOffset(newOffset);
        this.start = newOffset;
        this.state = State.ITER_CHECK_FWD;
    }

    public int getOffset() {
        if (this.state.compareTo(State.ITER_CHECK_BWD) <= 0) {
            return this.iter.getIndex();
        }
        if (this.state == State.ITER_IN_FCD_SEGMENT) {
            return this.pos;
        }
        if (this.pos == 0) {
            return this.start;
        }
        return this.limit;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public int nextCodePoint() {
        int c;
        Object obj = null;
        while (true) {
            if (this.state != State.ITER_CHECK_FWD) {
                if (this.state == State.ITER_IN_FCD_SEGMENT && this.pos != this.limit) {
                    break;
                } else if (this.state.compareTo(State.IN_NORM_ITER_AT_LIMIT) < 0 || this.pos == this.normalized.length()) {
                    switchToForward();
                } else {
                    c = this.normalized.codePointAt(this.pos);
                    this.pos += Character.charCount(c);
                    return c;
                }
            }
            c = this.iter.next();
            if (c < 0) {
                return c;
            }
            if (CollationFCD.hasTccc(c) && (CollationFCD.maybeTibetanCompositeVowel(c) || CollationFCD.hasLccc(this.iter.current()))) {
                this.iter.previous();
                if (!nextSegment()) {
                    return -1;
                }
            }
        }
        if (CollationIterator.isLeadSurrogate(c)) {
            int trail = this.iter.next();
            if (CollationIterator.isTrailSurrogate(trail)) {
                return Character.toCodePoint((char) c, (char) trail);
            }
            if (trail >= 0) {
                this.iter.previous();
            }
        }
        return c;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public int previousCodePoint() {
        int c;
        int i = 0;
        while (true) {
            if (this.state != State.ITER_CHECK_BWD) {
                if (this.state == State.ITER_IN_FCD_SEGMENT && this.pos != this.start) {
                    break;
                } else if (this.state.compareTo(State.IN_NORM_ITER_AT_LIMIT) < 0 || this.pos == 0) {
                    switchToBackward();
                } else {
                    c = this.normalized.codePointBefore(this.pos);
                    this.pos -= Character.charCount(c);
                    return c;
                }
            }
            c = this.iter.previous();
            if (c >= 0) {
                if (!CollationFCD.hasLccc(c)) {
                    break;
                }
                int prev = -1;
                if (!CollationFCD.maybeTibetanCompositeVowel(c)) {
                    prev = this.iter.previous();
                    if (!CollationFCD.hasTccc(prev)) {
                        break;
                    }
                }
                this.iter.next();
                if (prev >= 0) {
                    this.iter.next();
                }
                if (!previousSegment()) {
                    return -1;
                }
            } else {
                this.pos = 0;
                this.start = 0;
                this.state = State.ITER_IN_FCD_SEGMENT;
                return -1;
            }
        }
        c = this.iter.previousCodePoint();
        this.pos -= Character.charCount(c);
        if (!-assertionsDisabled) {
            if (c >= 0) {
                i = 1;
            }
            if (i == 0) {
                throw new AssertionError();
            }
        }
        return c;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    protected long handleNextCE32() {
        int c;
        Object obj = null;
        while (true) {
            if (this.state != State.ITER_CHECK_FWD) {
                if (this.state != State.ITER_IN_FCD_SEGMENT || this.pos == this.limit) {
                    if (this.state.compareTo(State.IN_NORM_ITER_AT_LIMIT) >= 0 && this.pos != this.normalized.length()) {
                        break;
                    }
                    switchToForward();
                } else {
                    break;
                }
            }
            c = this.iter.next();
            if (c >= 0) {
                if (!CollationFCD.hasTccc(c) || (!CollationFCD.maybeTibetanCompositeVowel(c) && !CollationFCD.hasLccc(this.iter.current()))) {
                    break;
                }
                this.iter.previous();
                if (!nextSegment()) {
                    return 192;
                }
            } else {
                return -4294967104L;
            }
        }
        return makeCodePointAndCE32Pair(c, this.trie.getFromU16SingleLead((char) c));
    }

    protected char handleGetTrailSurrogate() {
        Object obj = null;
        if (this.state.compareTo(State.ITER_IN_FCD_SEGMENT) <= 0) {
            int trail = this.iter.next();
            if (CollationIterator.isTrailSurrogate(trail)) {
                if (this.state == State.ITER_IN_FCD_SEGMENT) {
                    this.pos++;
                }
            } else if (trail >= 0) {
                this.iter.previous();
            }
            return (char) trail;
        }
        if (!-assertionsDisabled) {
            if (this.pos < this.normalized.length()) {
                obj = 1;
            }
            if (obj == null) {
                throw new AssertionError();
            }
        }
        char trail2 = this.normalized.charAt(this.pos);
        if (Character.isLowSurrogate(trail2)) {
            this.pos++;
        }
        return trail2;
    }

    protected void forwardNumCodePoints(int num) {
        while (num > 0 && nextCodePoint() >= 0) {
            num--;
        }
    }

    protected void backwardNumCodePoints(int num) {
        while (num > 0 && previousCodePoint() >= 0) {
            num--;
        }
    }

    private void switchToForward() {
        Object obj = 1;
        if (!-assertionsDisabled) {
            if (this.state != State.ITER_CHECK_BWD && (!(this.state == State.ITER_IN_FCD_SEGMENT && this.pos == this.limit) && (this.state.compareTo(State.IN_NORM_ITER_AT_LIMIT) < 0 || this.pos != this.normalized.length()))) {
                obj = null;
            }
            if (obj == null) {
                throw new AssertionError();
            }
        }
        if (this.state == State.ITER_CHECK_BWD) {
            int index = this.iter.getIndex();
            this.pos = index;
            this.start = index;
            if (this.pos == this.limit) {
                this.state = State.ITER_CHECK_FWD;
                return;
            } else {
                this.state = State.ITER_IN_FCD_SEGMENT;
                return;
            }
        }
        if (this.state != State.ITER_IN_FCD_SEGMENT) {
            if (this.state == State.IN_NORM_ITER_AT_START) {
                this.iter.moveIndex(this.limit - this.start);
            }
            this.start = this.limit;
        }
        this.state = State.ITER_CHECK_FWD;
    }

    private boolean nextSegment() {
        int i = 0;
        if (!-assertionsDisabled) {
            if (!(this.state == State.ITER_CHECK_FWD)) {
                throw new AssertionError();
            }
        }
        this.pos = this.iter.getIndex();
        if (this.s == null) {
            this.s = new StringBuilder();
        } else {
            this.s.setLength(0);
        }
        int prevCC = 0;
        do {
            int c = this.iter.nextCodePoint();
            if (c >= 0) {
                int fcd16 = this.nfcImpl.getFCD16(c);
                int leadCC = fcd16 >> 8;
                if (leadCC == 0 && this.s.length() != 0) {
                    this.iter.previousCodePoint();
                    break;
                }
                this.s.appendCodePoint(c);
                if (leadCC == 0 || (prevCC <= leadCC && !CollationFCD.isFCD16OfTibetanCompositeVowel(fcd16))) {
                    prevCC = fcd16 & 255;
                } else {
                    while (true) {
                        c = this.iter.nextCodePoint();
                        if (c < 0) {
                            break;
                        } else if (this.nfcImpl.getFCD16(c) <= 255) {
                            break;
                        } else {
                            this.s.appendCodePoint(c);
                        }
                    }
                    this.iter.previousCodePoint();
                    normalize(this.s);
                    this.start = this.pos;
                    this.limit = this.pos + this.s.length();
                    this.state = State.IN_NORM_ITER_AT_LIMIT;
                    this.pos = 0;
                    return true;
                }
            }
            break;
        } while (prevCC != 0);
        this.limit = this.pos + this.s.length();
        if (!-assertionsDisabled) {
            if (this.pos != this.limit) {
                i = 1;
            }
            if (i == 0) {
                throw new AssertionError();
            }
        }
        this.iter.moveIndex(-this.s.length());
        this.state = State.ITER_IN_FCD_SEGMENT;
        return true;
    }

    private void switchToBackward() {
        Object obj = 1;
        if (!-assertionsDisabled) {
            if (this.state != State.ITER_CHECK_FWD && (!(this.state == State.ITER_IN_FCD_SEGMENT && this.pos == this.start) && (this.state.compareTo(State.IN_NORM_ITER_AT_LIMIT) < 0 || this.pos != 0))) {
                obj = null;
            }
            if (obj == null) {
                throw new AssertionError();
            }
        }
        if (this.state == State.ITER_CHECK_FWD) {
            int index = this.iter.getIndex();
            this.pos = index;
            this.limit = index;
            if (this.pos == this.start) {
                this.state = State.ITER_CHECK_BWD;
                return;
            } else {
                this.state = State.ITER_IN_FCD_SEGMENT;
                return;
            }
        }
        if (this.state != State.ITER_IN_FCD_SEGMENT) {
            if (this.state == State.IN_NORM_ITER_AT_LIMIT) {
                this.iter.moveIndex(this.start - this.limit);
            }
            this.limit = this.start;
        }
        this.state = State.ITER_CHECK_BWD;
    }

    private boolean previousSegment() {
        int i = 0;
        if (!-assertionsDisabled) {
            if (!(this.state == State.ITER_CHECK_BWD)) {
                throw new AssertionError();
            }
        }
        this.pos = this.iter.getIndex();
        if (this.s == null) {
            this.s = new StringBuilder();
        } else {
            this.s.setLength(0);
        }
        int nextCC = 0;
        do {
            int c = this.iter.previousCodePoint();
            if (c >= 0) {
                int fcd16 = this.nfcImpl.getFCD16(c);
                int trailCC = fcd16 & 255;
                if (trailCC == 0 && this.s.length() != 0) {
                    this.iter.nextCodePoint();
                    break;
                }
                this.s.appendCodePoint(c);
                if (trailCC == 0 || ((nextCC == 0 || trailCC <= nextCC) && !CollationFCD.isFCD16OfTibetanCompositeVowel(fcd16))) {
                    nextCC = fcd16 >> 8;
                } else {
                    while (fcd16 > 255) {
                        c = this.iter.previousCodePoint();
                        if (c < 0) {
                            break;
                        }
                        fcd16 = this.nfcImpl.getFCD16(c);
                        if (fcd16 == 0) {
                            this.iter.nextCodePoint();
                            break;
                        }
                        this.s.appendCodePoint(c);
                    }
                    this.s.reverse();
                    normalize(this.s);
                    this.limit = this.pos;
                    this.start = this.pos - this.s.length();
                    this.state = State.IN_NORM_ITER_AT_START;
                    this.pos = this.normalized.length();
                    return true;
                }
            }
            break;
        } while (nextCC != 0);
        this.start = this.pos - this.s.length();
        if (!-assertionsDisabled) {
            if (this.pos != this.start) {
                i = 1;
            }
            if (i == 0) {
                throw new AssertionError();
            }
        }
        this.iter.moveIndex(this.s.length());
        this.state = State.ITER_IN_FCD_SEGMENT;
        return true;
    }

    private void normalize(CharSequence s) {
        if (this.normalized == null) {
            this.normalized = new StringBuilder();
        }
        this.nfcImpl.decompose(s, this.normalized);
    }
}
