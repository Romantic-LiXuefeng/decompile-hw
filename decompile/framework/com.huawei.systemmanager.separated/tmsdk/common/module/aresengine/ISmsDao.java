package tmsdk.common.module.aresengine;

/* compiled from: Unknown */
public interface ISmsDao<T extends SmsEntity> {
    long insert(T t, FilterResult filterResult);
}
