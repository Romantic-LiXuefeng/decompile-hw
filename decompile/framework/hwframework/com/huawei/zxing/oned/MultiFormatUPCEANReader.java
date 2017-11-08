package com.huawei.zxing.oned;

import com.huawei.zxing.BarcodeFormat;
import com.huawei.zxing.DecodeHintType;
import com.huawei.zxing.NotFoundException;
import com.huawei.zxing.Reader;
import com.huawei.zxing.ReaderException;
import com.huawei.zxing.Result;
import com.huawei.zxing.common.BitArray;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public final class MultiFormatUPCEANReader extends OneDReader {
    private final UPCEANReader[] readers;

    public MultiFormatUPCEANReader(Map<DecodeHintType, ?> hints) {
        Collection collection;
        if (hints == null) {
            collection = null;
        } else {
            collection = (Collection) hints.get(DecodeHintType.POSSIBLE_FORMATS);
        }
        Collection<UPCEANReader> readers = new ArrayList();
        if (collection != null) {
            if (collection.contains(BarcodeFormat.EAN_13)) {
                readers.add(new EAN13Reader());
            } else if (collection.contains(BarcodeFormat.UPC_A)) {
                readers.add(new UPCAReader());
            }
            if (collection.contains(BarcodeFormat.EAN_8)) {
                readers.add(new EAN8Reader());
            }
            if (collection.contains(BarcodeFormat.UPC_E)) {
                readers.add(new UPCEReader());
            }
        }
        if (readers.isEmpty()) {
            readers.add(new EAN13Reader());
            readers.add(new EAN8Reader());
            readers.add(new UPCEReader());
        }
        this.readers = (UPCEANReader[]) readers.toArray(new UPCEANReader[readers.size()]);
    }

    public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType, ?> hints) throws NotFoundException {
        Collection<BarcodeFormat> possibleFormats = null;
        int[] startGuardPattern = UPCEANReader.findStartGuardPattern(row);
        UPCEANReader[] uPCEANReaderArr = this.readers;
        int length = uPCEANReaderArr.length;
        int i = 0;
        while (i < length) {
            try {
                Result result = uPCEANReaderArr[i].decodeRow(rowNumber, row, startGuardPattern, hints);
                boolean ean13MayBeUPCA = result.getBarcodeFormat() == BarcodeFormat.EAN_13 ? result.getText().charAt(0) == '0' : false;
                if (hints != null) {
                    possibleFormats = (Collection) hints.get(DecodeHintType.POSSIBLE_FORMATS);
                }
                boolean contains = possibleFormats != null ? possibleFormats.contains(BarcodeFormat.UPC_A) : true;
                if (!ean13MayBeUPCA || !contains) {
                    return result;
                }
                Result resultUPCA = new Result(result.getText().substring(1), result.getRawBytes(), result.getResultPoints(), BarcodeFormat.UPC_A);
                resultUPCA.putAllMetadata(result.getResultMetadata());
                return resultUPCA;
            } catch (ReaderException e) {
                i++;
            }
        }
        throw NotFoundException.getNotFoundInstance();
    }

    public void reset() {
        for (Reader reader : this.readers) {
            reader.reset();
        }
    }
}
