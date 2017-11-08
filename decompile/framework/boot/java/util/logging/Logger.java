package java.util.logging;

import dalvik.system.VMStack;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import sun.reflect.CallerSensitive;

public class Logger {
    public static final String GLOBAL_LOGGER_NAME = "global";
    static final String SYSTEM_LOGGER_RB_NAME = "sun.util.logging.resources.logging";
    private static final Handler[] emptyHandlers = new Handler[0];
    @Deprecated
    public static final Logger global = new Logger(GLOBAL_LOGGER_NAME);
    private static final int offValue = Level.OFF.intValue();
    private static Object treeLock = new Object();
    private boolean anonymous;
    private WeakReference<ClassLoader> callersClassLoaderRef;
    private ResourceBundle catalog;
    private Locale catalogLocale;
    private String catalogName;
    private volatile Filter filter;
    private final CopyOnWriteArrayList<Handler> handlers;
    private ArrayList<LoggerWeakRef> kids;
    private volatile Level levelObject;
    private volatile int levelValue;
    private LogManager manager;
    private String name;
    private volatile Logger parent;
    private String resourceBundleName;
    private volatile boolean useParentHandlers;

    private static class LoggerHelper {
        static boolean allowStackWalkSearch = getBooleanProperty("jdk.logging.allowStackWalkSearch");
        static boolean disableCallerCheck = getBooleanProperty("sun.util.logging.disableCallerCheck");

        private LoggerHelper() {
        }

        private static boolean getBooleanProperty(final String key) {
            return Boolean.valueOf((String) AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty(key);
                }
            })).booleanValue();
        }
    }

    public static final Logger getGlobal() {
        return global;
    }

    protected Logger(String name, String resourceBundleName) {
        this(name, resourceBundleName, null);
    }

    Logger(String name, String resourceBundleName, Class<?> caller) {
        this.handlers = new CopyOnWriteArrayList();
        this.useParentHandlers = true;
        this.manager = LogManager.getLogManager();
        setupResourceInfo(resourceBundleName, caller);
        this.name = name;
        this.levelValue = Level.INFO.intValue();
    }

    private void setCallersClassLoaderRef(Class<?> caller) {
        ClassLoader callersClassLoader = null;
        if (caller != null) {
            callersClassLoader = caller.getClassLoader();
        }
        if (callersClassLoader != null) {
            this.callersClassLoaderRef = new WeakReference(callersClassLoader);
        }
    }

    private ClassLoader getCallersClassLoader() {
        if (this.callersClassLoaderRef != null) {
            return (ClassLoader) this.callersClassLoaderRef.get();
        }
        return null;
    }

    private Logger(String name) {
        this.handlers = new CopyOnWriteArrayList();
        this.useParentHandlers = true;
        this.name = name;
        this.levelValue = Level.INFO.intValue();
    }

    void setLogManager(LogManager manager) {
        this.manager = manager;
    }

    private void checkPermission() throws SecurityException {
        if (!this.anonymous) {
            if (this.manager == null) {
                this.manager = LogManager.getLogManager();
            }
            this.manager.checkPermission();
        }
    }

    private static Logger demandLogger(String name, String resourceBundleName, Class<?> caller) {
        LogManager manager = LogManager.getLogManager();
        if (System.getSecurityManager() == null || LoggerHelper.disableCallerCheck || caller.getClassLoader() != null) {
            return manager.demandLogger(name, resourceBundleName, caller);
        }
        return manager.demandSystemLogger(name, resourceBundleName);
    }

    @CallerSensitive
    public static Logger getLogger(String name) {
        return demandLogger(name, null, VMStack.getStackClass1());
    }

    @CallerSensitive
    public static Logger getLogger(String name, String resourceBundleName) {
        Class<?> callerClass = VMStack.getStackClass1();
        Logger result = demandLogger(name, resourceBundleName, callerClass);
        if (result.resourceBundleName == null) {
            result.setupResourceInfo(resourceBundleName, callerClass);
        } else if (!result.resourceBundleName.equals(resourceBundleName)) {
            throw new IllegalArgumentException(result.resourceBundleName + " != " + resourceBundleName);
        }
        return result;
    }

    static Logger getPlatformLogger(String name) {
        return LogManager.getLogManager().demandSystemLogger(name, SYSTEM_LOGGER_RB_NAME);
    }

    public static Logger getAnonymousLogger() {
        return getAnonymousLogger(null);
    }

    @CallerSensitive
    public static Logger getAnonymousLogger(String resourceBundleName) {
        LogManager manager = LogManager.getLogManager();
        manager.drainLoggerRefQueueBounded();
        Logger result = new Logger(null, resourceBundleName, VMStack.getStackClass1());
        result.anonymous = true;
        result.doSetParent(manager.getLogger(""));
        return result;
    }

    public ResourceBundle getResourceBundle() {
        return findResourceBundle(getResourceBundleName(), true);
    }

    public String getResourceBundleName() {
        return this.resourceBundleName;
    }

    public void setFilter(Filter newFilter) throws SecurityException {
        checkPermission();
        this.filter = newFilter;
    }

    public Filter getFilter() {
        return this.filter;
    }

    public void log(LogRecord record) {
        if (record.getLevel().intValue() >= this.levelValue && this.levelValue != offValue) {
            Filter theFilter = this.filter;
            if (theFilter == null || theFilter.isLoggable(record)) {
                for (Logger logger = this; logger != null; logger = logger.getParent()) {
                    for (Handler handler : logger.getHandlers()) {
                        handler.publish(record);
                    }
                    if (!logger.getUseParentHandlers()) {
                        break;
                    }
                }
            }
        }
    }

    private void doLog(LogRecord lr) {
        lr.setLoggerName(this.name);
        String ebname = getEffectiveResourceBundleName();
        if (!(ebname == null || ebname.equals(SYSTEM_LOGGER_RB_NAME))) {
            lr.setResourceBundleName(ebname);
            lr.setResourceBundle(findResourceBundle(ebname, true));
        }
        log(lr);
    }

    public void log(Level level, String msg) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            doLog(new LogRecord(level, msg));
        }
    }

    public void log(Level level, String msg, Object param1) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setParameters(new Object[]{param1});
            doLog(lr);
        }
    }

    public void log(Level level, String msg, Object[] params) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setParameters(params);
            doLog(lr);
        }
    }

    public void log(Level level, String msg, Throwable thrown) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setThrown(thrown);
            doLog(lr);
        }
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            doLog(lr);
        }
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            lr.setParameters(new Object[]{param1});
            doLog(lr);
        }
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            lr.setParameters(params);
            doLog(lr);
        }
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            lr.setThrown(thrown);
            doLog(lr);
        }
    }

    private void doLog(LogRecord lr, String rbname) {
        lr.setLoggerName(this.name);
        if (rbname != null) {
            lr.setResourceBundleName(rbname);
            lr.setResourceBundle(findResourceBundle(rbname, false));
        }
        log(lr);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            doLog(lr, bundleName);
        }
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object param1) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            lr.setParameters(new Object[]{param1});
            doLog(lr, bundleName);
        }
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object[] params) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            lr.setParameters(params);
            doLog(lr, bundleName);
        }
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Throwable thrown) {
        if (level.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(level, msg);
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            lr.setThrown(thrown);
            doLog(lr, bundleName);
        }
    }

    public void entering(String sourceClass, String sourceMethod) {
        if (Level.FINER.intValue() >= this.levelValue) {
            logp(Level.FINER, sourceClass, sourceMethod, "ENTRY");
        }
    }

    public void entering(String sourceClass, String sourceMethod, Object param1) {
        if (Level.FINER.intValue() >= this.levelValue) {
            String str = sourceClass;
            String str2 = sourceMethod;
            logp(Level.FINER, str, str2, "ENTRY {0}", new Object[]{param1});
        }
    }

    public void entering(String sourceClass, String sourceMethod, Object[] params) {
        if (Level.FINER.intValue() >= this.levelValue) {
            String msg = "ENTRY";
            if (params == null) {
                logp(Level.FINER, sourceClass, sourceMethod, msg);
                return;
            }
            for (int i = 0; i < params.length; i++) {
                msg = msg + " {" + i + "}";
            }
            logp(Level.FINER, sourceClass, sourceMethod, msg, params);
        }
    }

    public void exiting(String sourceClass, String sourceMethod) {
        if (Level.FINER.intValue() >= this.levelValue) {
            logp(Level.FINER, sourceClass, sourceMethod, "RETURN");
        }
    }

    public void exiting(String sourceClass, String sourceMethod, Object result) {
        if (Level.FINER.intValue() >= this.levelValue) {
            new Object[1][0] = result;
            logp(Level.FINER, sourceClass, sourceMethod, "RETURN {0}", result);
        }
    }

    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        if (Level.FINER.intValue() >= this.levelValue && this.levelValue != offValue) {
            LogRecord lr = new LogRecord(Level.FINER, "THROW");
            lr.setSourceClassName(sourceClass);
            lr.setSourceMethodName(sourceMethod);
            lr.setThrown(thrown);
            doLog(lr);
        }
    }

    public void severe(String msg) {
        if (Level.SEVERE.intValue() >= this.levelValue) {
            log(Level.SEVERE, msg);
        }
    }

    public void warning(String msg) {
        if (Level.WARNING.intValue() >= this.levelValue) {
            log(Level.WARNING, msg);
        }
    }

    public void info(String msg) {
        if (Level.INFO.intValue() >= this.levelValue) {
            log(Level.INFO, msg);
        }
    }

    public void config(String msg) {
        if (Level.CONFIG.intValue() >= this.levelValue) {
            log(Level.CONFIG, msg);
        }
    }

    public void fine(String msg) {
        if (Level.FINE.intValue() >= this.levelValue) {
            log(Level.FINE, msg);
        }
    }

    public void finer(String msg) {
        if (Level.FINER.intValue() >= this.levelValue) {
            log(Level.FINER, msg);
        }
    }

    public void finest(String msg) {
        if (Level.FINEST.intValue() >= this.levelValue) {
            log(Level.FINEST, msg);
        }
    }

    public void setLevel(Level newLevel) throws SecurityException {
        checkPermission();
        synchronized (treeLock) {
            this.levelObject = newLevel;
            updateEffectiveLevel();
        }
    }

    public Level getLevel() {
        return this.levelObject;
    }

    public boolean isLoggable(Level level) {
        if (level.intValue() < this.levelValue || this.levelValue == offValue) {
            return false;
        }
        return true;
    }

    public String getName() {
        return this.name;
    }

    public void addHandler(Handler handler) throws SecurityException {
        handler.getClass();
        checkPermission();
        this.handlers.add(handler);
    }

    public void removeHandler(Handler handler) throws SecurityException {
        checkPermission();
        if (handler != null) {
            this.handlers.remove(handler);
        }
    }

    public Handler[] getHandlers() {
        return (Handler[]) this.handlers.toArray(emptyHandlers);
    }

    public void setUseParentHandlers(boolean useParentHandlers) {
        checkPermission();
        this.useParentHandlers = useParentHandlers;
    }

    public boolean getUseParentHandlers() {
        return this.useParentHandlers;
    }

    private static ResourceBundle findSystemResourceBundle(final Locale locale) {
        return (ResourceBundle) AccessController.doPrivileged(new PrivilegedAction<ResourceBundle>() {
            public ResourceBundle run() {
                try {
                    return ResourceBundle.getBundle(Logger.SYSTEM_LOGGER_RB_NAME, locale, ClassLoader.getSystemClassLoader());
                } catch (MissingResourceException e) {
                    throw new InternalError(e.toString());
                }
            }
        });
    }

    private synchronized ResourceBundle findResourceBundle(String name, boolean useCallersClassLoader) {
        if (name == null) {
            return null;
        }
        Locale currentLocale = Locale.getDefault();
        if (this.catalog != null && currentLocale.equals(this.catalogLocale) && name.equals(this.catalogName)) {
            return this.catalog;
        } else if (name.equals(SYSTEM_LOGGER_RB_NAME)) {
            this.catalog = findSystemResourceBundle(currentLocale);
            this.catalogName = name;
            this.catalogLocale = currentLocale;
            return this.catalog;
        } else {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            try {
                this.catalog = ResourceBundle.getBundle(name, currentLocale, cl);
                this.catalogName = name;
                this.catalogLocale = currentLocale;
                return this.catalog;
            } catch (MissingResourceException e) {
                if (useCallersClassLoader) {
                    ClassLoader callersClassLoader = getCallersClassLoader();
                    if (!(callersClassLoader == null || callersClassLoader == cl)) {
                        try {
                            this.catalog = ResourceBundle.getBundle(name, currentLocale, callersClassLoader);
                            this.catalogName = name;
                            this.catalogLocale = currentLocale;
                            return this.catalog;
                        } catch (MissingResourceException e2) {
                            if (LoggerHelper.allowStackWalkSearch) {
                                return null;
                            }
                            return findResourceBundleFromStack(name, currentLocale, cl);
                        }
                    }
                }
                if (LoggerHelper.allowStackWalkSearch) {
                    return null;
                }
                return findResourceBundleFromStack(name, currentLocale, cl);
            }
        }
    }

    @CallerSensitive
    private synchronized ResourceBundle findResourceBundleFromStack(String name, Locale locale, ClassLoader cl) {
        StackTraceElement[] stack = VMStack.getThreadStackTrace(Thread.currentThread());
        int ix = 0;
        while (true) {
            Class clz = null;
            try {
                clz = Class.forName(stack[ix].getClassName());
            } catch (ClassNotFoundException e) {
            }
            if (clz == null) {
                return null;
            }
            ClassLoader cl2 = clz.getClassLoader();
            if (cl2 == null) {
                cl2 = ClassLoader.getSystemClassLoader();
            }
            if (cl != cl2) {
                cl = cl2;
                try {
                    this.catalog = ResourceBundle.getBundle(name, locale, cl);
                    this.catalogName = name;
                    this.catalogLocale = locale;
                    break;
                } catch (MissingResourceException e2) {
                }
            }
            ix++;
        }
        return this.catalog;
    }

    private synchronized void setupResourceInfo(String name, Class<?> callersClass) {
        if (name != null) {
            setCallersClassLoaderRef(callersClass);
            if (findResourceBundle(name, true) == null) {
                this.callersClassLoaderRef = null;
                throw new MissingResourceException("Can't find " + name + " bundle", name, "");
            } else {
                this.resourceBundleName = name;
            }
        }
    }

    public Logger getParent() {
        return this.parent;
    }

    public void setParent(Logger parent) {
        if (parent == null) {
            throw new NullPointerException();
        }
        this.manager.checkPermission();
        doSetParent(parent);
    }

    private void doSetParent(Logger newParent) {
        Throwable th;
        synchronized (treeLock) {
            LoggerWeakRef ref = null;
            try {
                LoggerWeakRef ref2;
                if (this.parent != null) {
                    Iterator<LoggerWeakRef> iter = this.parent.kids.iterator();
                    while (iter.hasNext()) {
                        ref = (LoggerWeakRef) iter.next();
                        if (((Logger) ref.get()) == this) {
                            iter.remove();
                            ref2 = ref;
                            break;
                        }
                        ref = null;
                    }
                }
                ref2 = ref;
                try {
                    this.parent = newParent;
                    if (this.parent.kids == null) {
                        this.parent.kids = new ArrayList(2);
                    }
                    if (ref2 == null) {
                        LogManager logManager = this.manager;
                        logManager.getClass();
                        ref = new LoggerWeakRef(this);
                    } else {
                        ref = ref2;
                    }
                    ref.setParentRef(new WeakReference(this.parent));
                    this.parent.kids.add(ref);
                    updateEffectiveLevel();
                } catch (Throwable th2) {
                    th = th2;
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    final void removeChildLogger(LoggerWeakRef child) {
        synchronized (treeLock) {
            Iterator<LoggerWeakRef> iter = this.kids.iterator();
            while (iter.hasNext()) {
                if (((LoggerWeakRef) iter.next()) == child) {
                    iter.remove();
                    return;
                }
            }
        }
    }

    private void updateEffectiveLevel() {
        int newLevelValue;
        if (this.levelObject != null) {
            newLevelValue = this.levelObject.intValue();
        } else if (this.parent != null) {
            newLevelValue = this.parent.levelValue;
        } else {
            newLevelValue = Level.INFO.intValue();
        }
        if (this.levelValue != newLevelValue) {
            this.levelValue = newLevelValue;
            if (this.kids != null) {
                for (int i = 0; i < this.kids.size(); i++) {
                    Logger kid = (Logger) ((LoggerWeakRef) this.kids.get(i)).get();
                    if (kid != null) {
                        kid.updateEffectiveLevel();
                    }
                }
            }
        }
    }

    private String getEffectiveResourceBundleName() {
        for (Logger target = this; target != null; target = target.getParent()) {
            String rbn = target.getResourceBundleName();
            if (rbn != null) {
                return rbn;
            }
        }
        return null;
    }
}
