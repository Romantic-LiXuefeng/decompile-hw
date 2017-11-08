package tmsdk.bg.module.aresengine;

import tmsdk.common.module.aresengine.CallLogEntity;
import tmsdk.common.module.aresengine.SystemCallLogFilterConsts;

/* compiled from: Unknown */
public abstract class SystemCallLogFilter extends DataFilter<CallLogEntity> implements SystemCallLogFilterConsts {
    public abstract void setShortCallChecker(IShortCallChecker iShortCallChecker);
}
