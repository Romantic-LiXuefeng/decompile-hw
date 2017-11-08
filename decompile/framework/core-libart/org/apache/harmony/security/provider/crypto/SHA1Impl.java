package org.apache.harmony.security.provider.crypto;

public class SHA1Impl {
    static void computeHash(int[] r1) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: org.apache.harmony.security.provider.crypto.SHA1Impl.computeHash(int[]):void
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 5 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: org.apache.harmony.security.provider.crypto.SHA1Impl.computeHash(int[]):void");
    }

    static void updateHash(int[] intArray, byte[] byteInput, int fromByte, int toByte) {
        int index = intArray[81];
        int i = fromByte;
        int wordIndex = index >> 2;
        int byteIndex = index & 3;
        intArray[81] = (((index + toByte) - fromByte) + 1) & 63;
        if (byteIndex != 0) {
            while (i <= toByte && byteIndex < 4) {
                intArray[wordIndex] = intArray[wordIndex] | ((byteInput[i] & 255) << ((3 - byteIndex) << 3));
                byteIndex++;
                i++;
            }
            if (byteIndex == 4) {
                wordIndex++;
                if (wordIndex == 16) {
                    computeHash(intArray);
                    wordIndex = 0;
                }
            }
            if (i > toByte) {
                return;
            }
        }
        int maxWord = ((toByte - i) + 1) >> 2;
        for (int k = 0; k < maxWord; k++) {
            intArray[wordIndex] = ((((byteInput[i] & 255) << 24) | ((byteInput[i + 1] & 255) << 16)) | ((byteInput[i + 2] & 255) << 8)) | (byteInput[i + 3] & 255);
            i += 4;
            wordIndex++;
            if (wordIndex >= 16) {
                computeHash(intArray);
                wordIndex = 0;
            }
        }
        int nBytes = (toByte - i) + 1;
        if (nBytes != 0) {
            int w = (byteInput[i] & 255) << 24;
            if (nBytes != 1) {
                w |= (byteInput[i + 1] & 255) << 16;
                if (nBytes != 2) {
                    w |= (byteInput[i + 2] & 255) << 8;
                }
            }
            intArray[wordIndex] = w;
        }
    }
}
