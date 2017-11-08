package android.print;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import com.android.internal.util.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

public final class PrinterCapabilitiesInfo implements Parcelable {
    public static final Creator<PrinterCapabilitiesInfo> CREATOR = new Creator<PrinterCapabilitiesInfo>() {
        public PrinterCapabilitiesInfo createFromParcel(Parcel parcel) {
            return new PrinterCapabilitiesInfo(parcel);
        }

        public PrinterCapabilitiesInfo[] newArray(int size) {
            return new PrinterCapabilitiesInfo[size];
        }
    };
    private static final Margins DEFAULT_MARGINS = new Margins(0, 0, 0, 0);
    public static final int DEFAULT_UNDEFINED = -1;
    private static final int PROPERTY_COLOR_MODE = 2;
    private static final int PROPERTY_COUNT = 4;
    private static final int PROPERTY_DUPLEX_MODE = 3;
    private static final int PROPERTY_MEDIA_SIZE = 0;
    private static final int PROPERTY_RESOLUTION = 1;
    private int mColorModes;
    private final int[] mDefaults;
    private int mDuplexModes;
    private List<MediaSize> mMediaSizes;
    private Margins mMinMargins;
    private List<Resolution> mResolutions;

    final /* synthetic */ class -void__init__android_os_Parcel_parcel_LambdaImpl0 implements IntConsumer {
        public void accept(int arg0) {
            PrintAttributes.enforceValidColorMode(arg0);
        }
    }

    final /* synthetic */ class -void__init__android_os_Parcel_parcel_LambdaImpl1 implements IntConsumer {
        public void accept(int arg0) {
            PrintAttributes.enforceValidDuplexMode(arg0);
        }
    }

    public static final class Builder {
        private final PrinterCapabilitiesInfo mPrototype;

        public Builder(PrinterId printerId) {
            if (printerId == null) {
                throw new IllegalArgumentException("printerId cannot be null.");
            }
            this.mPrototype = new PrinterCapabilitiesInfo();
        }

        public Builder addMediaSize(MediaSize mediaSize, boolean isDefault) {
            if (this.mPrototype.mMediaSizes == null) {
                this.mPrototype.mMediaSizes = new ArrayList();
            }
            int insertionIndex = this.mPrototype.mMediaSizes.size();
            this.mPrototype.mMediaSizes.add(mediaSize);
            if (isDefault) {
                throwIfDefaultAlreadySpecified(0);
                this.mPrototype.mDefaults[0] = insertionIndex;
            }
            return this;
        }

        public Builder addResolution(Resolution resolution, boolean isDefault) {
            if (this.mPrototype.mResolutions == null) {
                this.mPrototype.mResolutions = new ArrayList();
            }
            int insertionIndex = this.mPrototype.mResolutions.size();
            this.mPrototype.mResolutions.add(resolution);
            if (isDefault) {
                throwIfDefaultAlreadySpecified(1);
                this.mPrototype.mDefaults[1] = insertionIndex;
            }
            return this;
        }

        public Builder setMinMargins(Margins margins) {
            if (margins == null) {
                throw new IllegalArgumentException("margins cannot be null");
            }
            this.mPrototype.mMinMargins = margins;
            return this;
        }

        public Builder setColorModes(int colorModes, int defaultColorMode) {
            PrinterCapabilitiesInfo.enforceValidMask(colorModes, new PrinterCapabilitiesInfo$Builder$-android_print_PrinterCapabilitiesInfo$Builder_setColorModes_int_colorModes_int_defaultColorMode_LambdaImpl0());
            PrintAttributes.enforceValidColorMode(defaultColorMode);
            this.mPrototype.mColorModes = colorModes;
            this.mPrototype.mDefaults[2] = defaultColorMode;
            return this;
        }

        public Builder setDuplexModes(int duplexModes, int defaultDuplexMode) {
            PrinterCapabilitiesInfo.enforceValidMask(duplexModes, new PrinterCapabilitiesInfo$Builder$-android_print_PrinterCapabilitiesInfo$Builder_setDuplexModes_int_duplexModes_int_defaultDuplexMode_LambdaImpl0());
            PrintAttributes.enforceValidDuplexMode(defaultDuplexMode);
            this.mPrototype.mDuplexModes = duplexModes;
            this.mPrototype.mDefaults[3] = defaultDuplexMode;
            return this;
        }

        public PrinterCapabilitiesInfo build() {
            if (this.mPrototype.mMediaSizes == null || this.mPrototype.mMediaSizes.isEmpty()) {
                throw new IllegalStateException("No media size specified.");
            } else if (this.mPrototype.mDefaults[0] == -1) {
                throw new IllegalStateException("No default media size specified.");
            } else if (this.mPrototype.mResolutions == null || this.mPrototype.mResolutions.isEmpty()) {
                throw new IllegalStateException("No resolution specified.");
            } else if (this.mPrototype.mDefaults[1] == -1) {
                throw new IllegalStateException("No default resolution specified.");
            } else if (this.mPrototype.mColorModes == 0) {
                throw new IllegalStateException("No color mode specified.");
            } else if (this.mPrototype.mDefaults[2] == -1) {
                throw new IllegalStateException("No default color mode specified.");
            } else {
                if (this.mPrototype.mDuplexModes == 0) {
                    setDuplexModes(1, 1);
                }
                if (this.mPrototype.mMinMargins != null) {
                    return this.mPrototype;
                }
                throw new IllegalArgumentException("margins cannot be null");
            }
        }

        private void throwIfDefaultAlreadySpecified(int propertyIndex) {
            if (this.mPrototype.mDefaults[propertyIndex] != -1) {
                throw new IllegalArgumentException("Default already specified.");
            }
        }
    }

    private java.lang.String colorModesToString() {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: android.print.PrinterCapabilitiesInfo.colorModesToString():java.lang.String
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 7 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: android.print.PrinterCapabilitiesInfo.colorModesToString():java.lang.String");
    }

    private java.lang.String duplexModesToString() {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: android.print.PrinterCapabilitiesInfo.duplexModesToString():java.lang.String
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 7 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: android.print.PrinterCapabilitiesInfo.duplexModesToString():java.lang.String");
    }

    private static void enforceValidMask(int r1, java.util.function.IntConsumer r2) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: android.print.PrinterCapabilitiesInfo.enforceValidMask(int, java.util.function.IntConsumer):void
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 7 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: android.print.PrinterCapabilitiesInfo.enforceValidMask(int, java.util.function.IntConsumer):void");
    }

    public PrinterCapabilitiesInfo() {
        this.mMinMargins = DEFAULT_MARGINS;
        this.mDefaults = new int[4];
        Arrays.fill(this.mDefaults, -1);
    }

    public PrinterCapabilitiesInfo(PrinterCapabilitiesInfo prototype) {
        this.mMinMargins = DEFAULT_MARGINS;
        this.mDefaults = new int[4];
        copyFrom(prototype);
    }

    public void copyFrom(PrinterCapabilitiesInfo other) {
        if (this != other) {
            this.mMinMargins = other.mMinMargins;
            if (other.mMediaSizes == null) {
                this.mMediaSizes = null;
            } else if (this.mMediaSizes != null) {
                this.mMediaSizes.clear();
                this.mMediaSizes.addAll(other.mMediaSizes);
            } else {
                this.mMediaSizes = new ArrayList(other.mMediaSizes);
            }
            if (other.mResolutions == null) {
                this.mResolutions = null;
            } else if (this.mResolutions != null) {
                this.mResolutions.clear();
                this.mResolutions.addAll(other.mResolutions);
            } else {
                this.mResolutions = new ArrayList(other.mResolutions);
            }
            this.mColorModes = other.mColorModes;
            this.mDuplexModes = other.mDuplexModes;
            int defaultCount = other.mDefaults.length;
            for (int i = 0; i < defaultCount; i++) {
                this.mDefaults[i] = other.mDefaults[i];
            }
        }
    }

    public List<MediaSize> getMediaSizes() {
        return Collections.unmodifiableList(this.mMediaSizes);
    }

    public List<Resolution> getResolutions() {
        return Collections.unmodifiableList(this.mResolutions);
    }

    public Margins getMinMargins() {
        return this.mMinMargins;
    }

    public int getColorModes() {
        return this.mColorModes;
    }

    public int getDuplexModes() {
        return this.mDuplexModes;
    }

    public PrintAttributes getDefaults() {
        android.print.PrintAttributes.Builder builder = new android.print.PrintAttributes.Builder();
        builder.setMinMargins(this.mMinMargins);
        int mediaSizeIndex = this.mDefaults[0];
        if (mediaSizeIndex >= 0) {
            builder.setMediaSize((MediaSize) this.mMediaSizes.get(mediaSizeIndex));
        }
        int resolutionIndex = this.mDefaults[1];
        if (resolutionIndex >= 0) {
            builder.setResolution((Resolution) this.mResolutions.get(resolutionIndex));
        }
        int colorMode = this.mDefaults[2];
        if (colorMode > 0) {
            builder.setColorMode(colorMode);
        }
        int duplexMode = this.mDefaults[3];
        if (duplexMode > 0) {
            builder.setDuplexMode(duplexMode);
        }
        return builder.build();
    }

    private PrinterCapabilitiesInfo(Parcel parcel) {
        boolean z = true;
        this.mMinMargins = DEFAULT_MARGINS;
        this.mDefaults = new int[4];
        this.mMinMargins = (Margins) Preconditions.checkNotNull(readMargins(parcel));
        readMediaSizes(parcel);
        readResolutions(parcel);
        this.mColorModes = parcel.readInt();
        enforceValidMask(this.mColorModes, new -void__init__android_os_Parcel_parcel_LambdaImpl0());
        this.mDuplexModes = parcel.readInt();
        enforceValidMask(this.mDuplexModes, new -void__init__android_os_Parcel_parcel_LambdaImpl1());
        readDefaults(parcel);
        Preconditions.checkArgument(this.mMediaSizes.size() > this.mDefaults[0]);
        if (this.mResolutions.size() <= this.mDefaults[1]) {
            z = false;
        }
        Preconditions.checkArgument(z);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int flags) {
        writeMargins(this.mMinMargins, parcel);
        writeMediaSizes(parcel);
        writeResolutions(parcel);
        parcel.writeInt(this.mColorModes);
        parcel.writeInt(this.mDuplexModes);
        writeDefaults(parcel);
    }

    public int hashCode() {
        int i = 0;
        int hashCode = ((((this.mMinMargins == null ? 0 : this.mMinMargins.hashCode()) + 31) * 31) + (this.mMediaSizes == null ? 0 : this.mMediaSizes.hashCode())) * 31;
        if (this.mResolutions != null) {
            i = this.mResolutions.hashCode();
        }
        return ((((((hashCode + i) * 31) + this.mColorModes) * 31) + this.mDuplexModes) * 31) + Arrays.hashCode(this.mDefaults);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrinterCapabilitiesInfo other = (PrinterCapabilitiesInfo) obj;
        if (this.mMinMargins == null) {
            if (other.mMinMargins != null) {
                return false;
            }
        } else if (!this.mMinMargins.equals(other.mMinMargins)) {
            return false;
        }
        if (this.mMediaSizes == null) {
            if (other.mMediaSizes != null) {
                return false;
            }
        } else if (!this.mMediaSizes.equals(other.mMediaSizes)) {
            return false;
        }
        if (this.mResolutions == null) {
            if (other.mResolutions != null) {
                return false;
            }
        } else if (!this.mResolutions.equals(other.mResolutions)) {
            return false;
        }
        return this.mColorModes == other.mColorModes && this.mDuplexModes == other.mDuplexModes && Arrays.equals(this.mDefaults, other.mDefaults);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrinterInfo{");
        builder.append("minMargins=").append(this.mMinMargins);
        builder.append(", mediaSizes=").append(this.mMediaSizes);
        builder.append(", resolutions=").append(this.mResolutions);
        builder.append(", colorModes=").append(colorModesToString());
        builder.append(", duplexModes=").append(duplexModesToString());
        builder.append("\"}");
        return builder.toString();
    }

    private void writeMediaSizes(Parcel parcel) {
        if (this.mMediaSizes == null) {
            parcel.writeInt(0);
            return;
        }
        int mediaSizeCount = this.mMediaSizes.size();
        parcel.writeInt(mediaSizeCount);
        for (int i = 0; i < mediaSizeCount; i++) {
            ((MediaSize) this.mMediaSizes.get(i)).writeToParcel(parcel);
        }
    }

    private void readMediaSizes(Parcel parcel) {
        int mediaSizeCount = parcel.readInt();
        if (mediaSizeCount > 0 && this.mMediaSizes == null) {
            this.mMediaSizes = new ArrayList();
        }
        for (int i = 0; i < mediaSizeCount; i++) {
            this.mMediaSizes.add(MediaSize.createFromParcel(parcel));
        }
    }

    private void writeResolutions(Parcel parcel) {
        if (this.mResolutions == null) {
            parcel.writeInt(0);
            return;
        }
        int resolutionCount = this.mResolutions.size();
        parcel.writeInt(resolutionCount);
        for (int i = 0; i < resolutionCount; i++) {
            ((Resolution) this.mResolutions.get(i)).writeToParcel(parcel);
        }
    }

    private void readResolutions(Parcel parcel) {
        int resolutionCount = parcel.readInt();
        if (resolutionCount > 0 && this.mResolutions == null) {
            this.mResolutions = new ArrayList();
        }
        for (int i = 0; i < resolutionCount; i++) {
            this.mResolutions.add(Resolution.createFromParcel(parcel));
        }
    }

    private void writeMargins(Margins margins, Parcel parcel) {
        if (margins == null) {
            parcel.writeInt(0);
            return;
        }
        parcel.writeInt(1);
        margins.writeToParcel(parcel);
    }

    private Margins readMargins(Parcel parcel) {
        return parcel.readInt() == 1 ? Margins.createFromParcel(parcel) : null;
    }

    private void readDefaults(Parcel parcel) {
        int defaultCount = parcel.readInt();
        for (int i = 0; i < defaultCount; i++) {
            this.mDefaults[i] = parcel.readInt();
        }
    }

    private void writeDefaults(Parcel parcel) {
        parcel.writeInt(defaultCount);
        for (int writeInt : this.mDefaults) {
            parcel.writeInt(writeInt);
        }
    }
}
