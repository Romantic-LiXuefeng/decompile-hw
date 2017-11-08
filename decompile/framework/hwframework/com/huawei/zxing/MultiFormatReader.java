package com.huawei.zxing;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import com.huawei.zxing.aztec.AztecReader;
import com.huawei.zxing.common.HybridBinarizer;
import com.huawei.zxing.datamatrix.DataMatrixReader;
import com.huawei.zxing.maxicode.MaxiCodeReader;
import com.huawei.zxing.multi.GenericMultipleBarcodeReader;
import com.huawei.zxing.oned.MultiFormatOneDReader;
import com.huawei.zxing.pdf417.PDF417Reader;
import com.huawei.zxing.qrcode.QRCodeReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public final class MultiFormatReader implements Reader {
    private Map<DecodeHintType, ?> hints;
    private Reader[] readers;

    public Result decode(BinaryBitmap image) throws NotFoundException {
        setHints(null);
        return decodeInternal(image);
    }

    public Result decode(BinaryBitmap image, Map<DecodeHintType, ?> hints) throws NotFoundException {
        setHints(hints);
        return decodeInternal(image);
    }

    public Result decodeWithState(BinaryBitmap image) throws NotFoundException {
        if (this.readers == null) {
            setHints(null);
        }
        return decodeInternal(image);
    }

    public Result decode(byte[] data, int width, int height, Rect rect) {
        if (data == null || rect == null) {
            return null;
        }
        Result rawResult = null;
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
        if (source != null) {
            try {
                rawResult = decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
            } catch (ReaderException e) {
            }
        }
        return rawResult;
    }

    public Result[] decodeWithBitmap(Bitmap image) {
        if (image == null) {
            return null;
        }
        int lWidth = image.getWidth();
        int lHeight = image.getHeight();
        int[] lPixels = new int[(lWidth * lHeight)];
        image.getPixels(lPixels, 0, lWidth, 0, 0, lWidth, lHeight);
        try {
            return new GenericMultipleBarcodeReader(this).decodeMultiple(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(lWidth, lHeight, lPixels))));
        } catch (NotFoundException e) {
            Log.e("MultiFormateReader", "not found, rawResult=null");
            return null;
        }
    }

    public void setHints(Map<DecodeHintType, ?> hints) {
        this.hints = hints;
        boolean containsKey = hints != null ? hints.containsKey(DecodeHintType.TRY_HARDER) : false;
        Collection collection = hints == null ? null : (Collection) hints.get(DecodeHintType.POSSIBLE_FORMATS);
        Collection<Reader> readers = new ArrayList();
        if (collection != null) {
            boolean addOneDReader;
            if (collection.contains(BarcodeFormat.UPC_A) || collection.contains(BarcodeFormat.UPC_E) || collection.contains(BarcodeFormat.EAN_13) || collection.contains(BarcodeFormat.EAN_8) || collection.contains(BarcodeFormat.CODABAR) || collection.contains(BarcodeFormat.CODE_39) || collection.contains(BarcodeFormat.CODE_93) || collection.contains(BarcodeFormat.CODE_128) || collection.contains(BarcodeFormat.ITF) || collection.contains(BarcodeFormat.RSS_14)) {
                addOneDReader = true;
            } else {
                addOneDReader = collection.contains(BarcodeFormat.RSS_EXPANDED);
            }
            if (addOneDReader && !containsKey) {
                readers.add(new MultiFormatOneDReader(hints));
            }
            if (collection.contains(BarcodeFormat.QR_CODE)) {
                readers.add(new QRCodeReader());
            }
            if (collection.contains(BarcodeFormat.DATA_MATRIX)) {
                readers.add(new DataMatrixReader());
            }
            if (collection.contains(BarcodeFormat.AZTEC)) {
                readers.add(new AztecReader());
            }
            if (collection.contains(BarcodeFormat.PDF_417)) {
                readers.add(new PDF417Reader());
            }
            if (collection.contains(BarcodeFormat.MAXICODE)) {
                readers.add(new MaxiCodeReader());
            }
            if (addOneDReader && containsKey) {
                readers.add(new MultiFormatOneDReader(hints));
            }
        }
        if (readers.isEmpty()) {
            readers.add(new QRCodeReader());
            readers.add(new DataMatrixReader());
            readers.add(new AztecReader());
            readers.add(new PDF417Reader());
            readers.add(new MaxiCodeReader());
            readers.add(new MultiFormatOneDReader(hints));
        }
        this.readers = (Reader[]) readers.toArray(new Reader[readers.size()]);
    }

    public void reset() {
        if (this.readers != null) {
            for (Reader reader : this.readers) {
                reader.reset();
            }
        }
    }

    private Result decodeInternal(BinaryBitmap image) throws NotFoundException {
        if (this.readers != null) {
            Reader[] readerArr = this.readers;
            int i = 0;
            int length = readerArr.length;
            while (i < length) {
                Reader reader = readerArr[i];
                Log.e("MultiFormatReader", "MultFormatReader current reader=" + reader);
                try {
                    return reader.decode(image, this.hints);
                } catch (ReaderException e) {
                    i++;
                }
            }
        }
        throw NotFoundException.getNotFoundInstance();
    }
}
