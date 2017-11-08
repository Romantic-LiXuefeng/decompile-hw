package com.android.server.wifi.anqp;

import com.android.server.wifi.anqp.Constants.ANQPElementType;
import java.net.ProtocolException;
import java.nio.ByteBuffer;

public class GEOLocationElement extends ANQPElement {
    private static final int ALT_FRACTION_SIZE = 8;
    private static final int ALT_TYPE_WIDTH = 4;
    private static final int ALT_WIDTH = 30;
    private static final int DATUM_WIDTH = 8;
    private static final int ELEMENT_ID = 123;
    private static final int GEO_LOCATION_LENGTH = 16;
    private static final int LL_FRACTION_SIZE = 25;
    private static final int LL_WIDTH = 34;
    private static final double LOG2_FACTOR = (1.0d / Math.log(2.0d));
    private static final int RES_WIDTH = 6;
    private final RealValue mAltitude;
    private final AltitudeType mAltitudeType;
    private final Datum mDatum;
    private final RealValue mLatitude;
    private final RealValue mLongitude;

    public enum AltitudeType {
        Unknown,
        Meters,
        Floors
    }

    private static class BitStream {
        private int bitOffset;
        private final byte[] data;

        private void append(long r1, int r3) {
            /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: com.android.server.wifi.anqp.GEOLocationElement.BitStream.append(long, int):void
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:256)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 6 more
*/
            /*
            // Can't load method instructions.
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.wifi.anqp.GEOLocationElement.BitStream.append(long, int):void");
        }

        private BitStream(int octets) {
            this.data = new byte[octets];
        }

        private byte[] getOctets() {
            return this.data;
        }
    }

    public enum Datum {
        Unknown,
        WGS84,
        NAD83Land,
        NAD83Water
    }

    public static class RealValue {
        private final int mResolution;
        private final boolean mResolutionSet;
        private final double mValue;

        public RealValue(double value) {
            this.mValue = value;
            this.mResolution = Integer.MIN_VALUE;
            this.mResolutionSet = false;
        }

        public RealValue(double value, int resolution) {
            this.mValue = value;
            this.mResolution = resolution;
            this.mResolutionSet = true;
        }

        public double getValue() {
            return this.mValue;
        }

        public boolean isResolutionSet() {
            return this.mResolutionSet;
        }

        public int getResolution() {
            return this.mResolution;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%f", new Object[]{Double.valueOf(this.mValue)}));
            if (this.mResolutionSet) {
                sb.append("+/-2^").append(this.mResolution);
            }
            return sb.toString();
        }
    }

    private static class ReverseBitStream {
        private int mBitoffset;
        private final byte[] mOctets;

        private ReverseBitStream(ByteBuffer octets) {
            this.mOctets = new byte[octets.remaining()];
            octets.get(this.mOctets);
        }

        private long sliceOff(int bits) {
            int bn = this.mBitoffset + bits;
            int remaining = bits;
            long value = 0;
            while (this.mBitoffset < bn) {
                int sbit = this.mBitoffset & 7;
                int octet = this.mBitoffset >>> 3;
                int width = Math.min(8 - sbit, remaining);
                value = (value << width) | ((long) getBits(this.mOctets[octet], sbit, width));
                this.mBitoffset += width;
                remaining -= width;
            }
            return value;
        }

        private static int getBits(byte b, int b0, int width) {
            return (b >> ((8 - b0) - width)) & ((1 << width) - 1);
        }
    }

    public GEOLocationElement(ANQPElementType infoID, ByteBuffer payload) throws ProtocolException {
        super(infoID);
        payload.get();
        int locLength = payload.get() & 255;
        if (locLength != 16) {
            throw new ProtocolException("GeoLocation length field value " + locLength + " incorrect, expected 16");
        } else if (payload.remaining() != 16) {
            throw new ProtocolException("Bad buffer length " + payload.remaining() + ", expected 16");
        } else {
            RealValue realValue;
            AltitudeType altitudeType;
            Datum datum;
            ReverseBitStream reverseBitStream = new ReverseBitStream(payload);
            int rawLatRes = (int) reverseBitStream.sliceOff(6);
            double latitude = fixToFloat(reverseBitStream.sliceOff(34), 25, 34);
            if (rawLatRes != 0) {
                realValue = new RealValue(latitude, bitsToAbsResolution((long) rawLatRes, 34, 25));
            } else {
                realValue = new RealValue(latitude);
            }
            this.mLatitude = r17;
            int rawLonRes = (int) reverseBitStream.sliceOff(6);
            double longitude = fixToFloat(reverseBitStream.sliceOff(34), 25, 34);
            if (rawLonRes != 0) {
                realValue = new RealValue(longitude, bitsToAbsResolution((long) rawLonRes, 34, 25));
            } else {
                realValue = new RealValue(longitude);
            }
            this.mLongitude = r17;
            int altType = (int) reverseBitStream.sliceOff(4);
            if (altType < AltitudeType.values().length) {
                altitudeType = AltitudeType.values()[altType];
            } else {
                altitudeType = AltitudeType.Unknown;
            }
            this.mAltitudeType = altitudeType;
            int rawAltRes = (int) reverseBitStream.sliceOff(6);
            double altitude = fixToFloat(reverseBitStream.sliceOff(30), 8, 30);
            if (rawAltRes != 0) {
                realValue = new RealValue(altitude, bitsToAbsResolution((long) rawAltRes, 30, 8));
            } else {
                realValue = new RealValue(altitude);
            }
            this.mAltitude = r17;
            int datumValue = (int) reverseBitStream.sliceOff(8);
            if (datumValue < Datum.values().length) {
                datum = Datum.values()[datumValue];
            } else {
                datum = Datum.Unknown;
            }
            this.mDatum = datum;
        }
    }

    public RealValue getLatitude() {
        return this.mLatitude;
    }

    public RealValue getLongitude() {
        return this.mLongitude;
    }

    public RealValue getAltitude() {
        return this.mAltitude;
    }

    public AltitudeType getAltitudeType() {
        return this.mAltitudeType;
    }

    public Datum getDatum() {
        return this.mDatum;
    }

    public String toString() {
        return "GEOLocation{mLatitude=" + this.mLatitude + ", mLongitude=" + this.mLongitude + ", mAltitude=" + this.mAltitude + ", mAltitudeType=" + this.mAltitudeType + ", mDatum=" + this.mDatum + '}';
    }

    static double fixToFloat(long value, int fractionSize, int width) {
        long sign = 1 << (width - 1);
        if ((value & sign) == 0) {
            return ((double) ((sign - 1) & value)) / ((double) (1 << fractionSize));
        }
        return (-((double) ((sign - 1) & (-value)))) / ((double) (1 << fractionSize));
    }

    private static long floatToFix(double value, int fractionSize, int width) {
        return Math.round(((double) (1 << fractionSize)) * value) & ((1 << width) - 1);
    }

    private static int getResolution(double variance) {
        return (int) Math.ceil(Math.log(variance) * LOG2_FACTOR);
    }

    private static int absResolutionToBits(int resolution, int fieldWidth, int fractionBits) {
        return ((fieldWidth - fractionBits) - 1) - resolution;
    }

    private static int bitsToAbsResolution(long bits, int fieldWidth, int fractionBits) {
        return ((fieldWidth - fractionBits) - 1) - ((int) bits);
    }
}
