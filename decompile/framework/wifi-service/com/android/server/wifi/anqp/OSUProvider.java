package com.android.server.wifi.anqp;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OSUProvider {
    private final int mHashCode;
    private final List<IconInfo> mIcons;
    private final List<I18Name> mNames;
    private final List<OSUMethod> mOSUMethods;
    private final String mOSUServer;
    private final String mOsuNai;
    private final List<I18Name> mServiceDescriptions;

    public enum OSUMethod {
        OmaDm,
        SoapXml
    }

    public OSUProvider(ByteBuffer payload) throws ProtocolException {
        if (payload.remaining() < 11) {
            throw new ProtocolException("Truncated OSU provider: " + payload.remaining());
        }
        int length = payload.getShort() & Constants.SHORT_MASK;
        int namesLength = payload.getShort() & Constants.SHORT_MASK;
        ByteBuffer namesBuffer = payload.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        namesBuffer.limit(namesBuffer.position() + namesLength);
        payload.position(payload.position() + namesLength);
        this.mNames = new ArrayList();
        while (namesBuffer.hasRemaining()) {
            this.mNames.add(new I18Name(namesBuffer));
        }
        this.mOSUServer = Constants.getPrefixedString(payload, 1, StandardCharsets.UTF_8);
        int methodLength = payload.get() & 255;
        this.mOSUMethods = new ArrayList(methodLength);
        while (methodLength > 0) {
            Object obj;
            int methodID = payload.get() & 255;
            List list = this.mOSUMethods;
            if (methodID < OSUMethod.values().length) {
                obj = OSUMethod.values()[methodID];
            } else {
                obj = null;
            }
            list.add(obj);
            methodLength--;
        }
        int iconsLength = payload.getShort() & Constants.SHORT_MASK;
        ByteBuffer iconsBuffer = payload.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        iconsBuffer.limit(iconsBuffer.position() + iconsLength);
        payload.position(payload.position() + iconsLength);
        this.mIcons = new ArrayList();
        while (iconsBuffer.hasRemaining()) {
            this.mIcons.add(new IconInfo(iconsBuffer));
        }
        this.mOsuNai = Constants.getPrefixedString(payload, 1, StandardCharsets.UTF_8, true);
        int descriptionsLength = payload.getShort() & Constants.SHORT_MASK;
        ByteBuffer descriptionsBuffer = payload.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        descriptionsBuffer.limit(descriptionsBuffer.position() + descriptionsLength);
        payload.position(payload.position() + descriptionsLength);
        this.mServiceDescriptions = new ArrayList();
        while (descriptionsBuffer.hasRemaining()) {
            this.mServiceDescriptions.add(new I18Name(descriptionsBuffer));
        }
        this.mHashCode = (((((((((this.mNames.hashCode() * 31) + this.mOSUServer.hashCode()) * 31) + this.mOSUMethods.hashCode()) * 31) + this.mIcons.hashCode()) * 31) + (this.mOsuNai != null ? this.mOsuNai.hashCode() : 0)) * 31) + this.mServiceDescriptions.hashCode();
    }

    public List<I18Name> getNames() {
        return this.mNames;
    }

    public String getOSUServer() {
        return this.mOSUServer;
    }

    public List<OSUMethod> getOSUMethods() {
        return this.mOSUMethods;
    }

    public List<IconInfo> getIcons() {
        return this.mIcons;
    }

    public String getOsuNai() {
        return this.mOsuNai;
    }

    public List<I18Name> getServiceDescriptions() {
        return this.mServiceDescriptions;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OSUProvider that = (OSUProvider) o;
        if (this.mOSUServer.equals(that.mOSUServer) && this.mNames.equals(that.mNames) && this.mServiceDescriptions.equals(that.mServiceDescriptions) && this.mIcons.equals(that.mIcons) && this.mOSUMethods.equals(that.mOSUMethods)) {
            return this.mOsuNai == null ? that.mOsuNai == null : this.mOsuNai.equals(that.mOsuNai);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.mHashCode;
    }

    public String toString() {
        return "OSUProvider{names=" + this.mNames + ", OSUServer='" + this.mOSUServer + '\'' + ", OSUMethods=" + this.mOSUMethods + ", icons=" + this.mIcons + ", NAI='" + this.mOsuNai + '\'' + ", serviceDescriptions=" + this.mServiceDescriptions + '}';
    }
}
