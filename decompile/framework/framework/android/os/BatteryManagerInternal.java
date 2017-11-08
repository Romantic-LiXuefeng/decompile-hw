package android.os;

public abstract class BatteryManagerInternal {
    public abstract int getBatteryLevel();

    public abstract boolean getBatteryLevelLow();

    public abstract int getInvalidCharger();

    public abstract int getPlugType();

    public abstract boolean isPowered(int i);

    public abstract void updateBatteryLight(boolean z, int i, int i2);
}
