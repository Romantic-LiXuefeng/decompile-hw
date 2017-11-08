package com.android.server.pm;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManagerInternal.PackagesProvider;
import android.content.pm.PackageManagerInternal.SyncAdapterPackagesProvider;
import android.content.pm.PackageParser.Package;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultPermissionGrantPolicy extends AbsDefaultPermissionGrantPolicy {
    private static final String AUDIO_MIME_TYPE = "audio/mpeg";
    private static final Set<String> CALENDAR_PERMISSIONS = new ArraySet();
    private static final Set<String> CAMERA_PERMISSIONS = new ArraySet();
    private static final Set<String> CONTACTS_PERMISSIONS = new ArraySet();
    private static final boolean DEBUG = false;
    private static final int DEFAULT_FLAGS = 786432;
    protected static final boolean HWFLOW;
    private static final Set<String> LOCATION_PERMISSIONS = new ArraySet();
    private static final Set<String> MICROPHONE_PERMISSIONS = new ArraySet();
    private static final Set<String> PHONE_PERMISSIONS = new ArraySet();
    private static final Set<String> SENSORS_PERMISSIONS = new ArraySet();
    private static final Set<String> SMS_PERMISSIONS = new ArraySet();
    private static final Set<String> STORAGE_PERMISSIONS = new ArraySet();
    private static final String TAG = "DefaultPermGrantPolicy";
    private PackagesProvider mDialerAppPackagesProvider;
    private PackagesProvider mLocationPackagesProvider;
    private final PackageManagerService mService;
    private PackagesProvider mSimCallManagerPackagesProvider;
    private PackagesProvider mSmsAppPackagesProvider;
    private SyncAdapterPackagesProvider mSyncAdapterPackagesProvider;
    private PackagesProvider mVoiceInteractionPackagesProvider;

    static {
        boolean isLoggable = !Log.HWINFO ? Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false : true;
        HWFLOW = isLoggable;
        PHONE_PERMISSIONS.add("android.permission.READ_PHONE_STATE");
        PHONE_PERMISSIONS.add("android.permission.CALL_PHONE");
        PHONE_PERMISSIONS.add("android.permission.READ_CALL_LOG");
        PHONE_PERMISSIONS.add("android.permission.WRITE_CALL_LOG");
        PHONE_PERMISSIONS.add("com.android.voicemail.permission.ADD_VOICEMAIL");
        PHONE_PERMISSIONS.add("android.permission.USE_SIP");
        PHONE_PERMISSIONS.add("android.permission.PROCESS_OUTGOING_CALLS");
        CONTACTS_PERMISSIONS.add("android.permission.READ_CONTACTS");
        CONTACTS_PERMISSIONS.add("android.permission.WRITE_CONTACTS");
        CONTACTS_PERMISSIONS.add("android.permission.GET_ACCOUNTS");
        LOCATION_PERMISSIONS.add("android.permission.ACCESS_FINE_LOCATION");
        LOCATION_PERMISSIONS.add("android.permission.ACCESS_COARSE_LOCATION");
        CALENDAR_PERMISSIONS.add("android.permission.READ_CALENDAR");
        CALENDAR_PERMISSIONS.add("android.permission.WRITE_CALENDAR");
        SMS_PERMISSIONS.add("android.permission.SEND_SMS");
        SMS_PERMISSIONS.add("android.permission.RECEIVE_SMS");
        SMS_PERMISSIONS.add("android.permission.READ_SMS");
        SMS_PERMISSIONS.add("android.permission.RECEIVE_WAP_PUSH");
        SMS_PERMISSIONS.add("android.permission.RECEIVE_MMS");
        SMS_PERMISSIONS.add("android.permission.READ_CELL_BROADCASTS");
        MICROPHONE_PERMISSIONS.add("android.permission.RECORD_AUDIO");
        CAMERA_PERMISSIONS.add("android.permission.CAMERA");
        SENSORS_PERMISSIONS.add("android.permission.BODY_SENSORS");
        STORAGE_PERMISSIONS.add("android.permission.READ_EXTERNAL_STORAGE");
        STORAGE_PERMISSIONS.add("android.permission.WRITE_EXTERNAL_STORAGE");
    }

    public DefaultPermissionGrantPolicy(PackageManagerService service) {
        this.mService = service;
    }

    public void setLocationPackagesProviderLPw(PackagesProvider provider) {
        this.mLocationPackagesProvider = provider;
    }

    public void setVoiceInteractionPackagesProviderLPw(PackagesProvider provider) {
        this.mVoiceInteractionPackagesProvider = provider;
    }

    public void setSmsAppPackagesProviderLPw(PackagesProvider provider) {
        this.mSmsAppPackagesProvider = provider;
    }

    public void setDialerAppPackagesProviderLPw(PackagesProvider provider) {
        this.mDialerAppPackagesProvider = provider;
    }

    public void setSimCallManagerPackagesProviderLPw(PackagesProvider provider) {
        this.mSimCallManagerPackagesProvider = provider;
    }

    public void setSyncAdapterPackagesProviderLPw(SyncAdapterPackagesProvider provider) {
        this.mSyncAdapterPackagesProvider = provider;
    }

    public void grantDefaultPermissions(int userId) {
        grantPermissionsToSysComponentsAndPrivApps(userId);
        grantDefaultSystemHandlerPermissions(userId);
    }

    private void grantPermissionsToSysComponentsAndPrivApps(int userId) {
        Log.i(TAG, "Granting permissions to platform components for user " + userId);
        synchronized (this.mService.mPackages) {
            for (Package pkg : this.mService.mPackages.values()) {
                if (isSysComponentOrPersistentPlatformSignedPrivAppLPr(pkg) && doesPackageSupportRuntimePermissions(pkg) && !pkg.requestedPermissions.isEmpty()) {
                    Set<String> permissions = new ArraySet();
                    int permissionCount = pkg.requestedPermissions.size();
                    for (int i = 0; i < permissionCount; i++) {
                        String permission = (String) pkg.requestedPermissions.get(i);
                        BasePermission bp = (BasePermission) this.mService.mSettings.mPermissions.get(permission);
                        if (bp != null && bp.isRuntime()) {
                            permissions.add(permission);
                        }
                    }
                    if (!permissions.isEmpty()) {
                        grantRuntimePermissionsLPw(pkg, permissions, true, userId);
                    }
                }
            }
        }
    }

    private void grantDefaultSystemHandlerPermissions(int userId) {
        PackagesProvider locationPackagesProvider;
        PackagesProvider voiceInteractionPackagesProvider;
        PackagesProvider smsAppPackagesProvider;
        PackagesProvider dialerAppPackagesProvider;
        PackagesProvider simCallManagerPackagesProvider;
        SyncAdapterPackagesProvider syncAdapterPackagesProvider;
        Log.i(TAG, "Granting permissions to default platform handlers for user " + userId);
        synchronized (this.mService.mPackages) {
            locationPackagesProvider = this.mLocationPackagesProvider;
            voiceInteractionPackagesProvider = this.mVoiceInteractionPackagesProvider;
            smsAppPackagesProvider = this.mSmsAppPackagesProvider;
            dialerAppPackagesProvider = this.mDialerAppPackagesProvider;
            simCallManagerPackagesProvider = this.mSimCallManagerPackagesProvider;
            syncAdapterPackagesProvider = this.mSyncAdapterPackagesProvider;
        }
        String[] packages = voiceInteractionPackagesProvider != null ? voiceInteractionPackagesProvider.getPackages(userId) : null;
        String[] packages2 = locationPackagesProvider != null ? locationPackagesProvider.getPackages(userId) : null;
        String[] packages3 = smsAppPackagesProvider != null ? smsAppPackagesProvider.getPackages(userId) : null;
        String[] packages4 = dialerAppPackagesProvider != null ? dialerAppPackagesProvider.getPackages(userId) : null;
        String[] packages5 = simCallManagerPackagesProvider != null ? simCallManagerPackagesProvider.getPackages(userId) : null;
        String[] packages6 = syncAdapterPackagesProvider != null ? syncAdapterPackagesProvider.getPackages("com.android.contacts", userId) : null;
        String[] packages7 = syncAdapterPackagesProvider != null ? syncAdapterPackagesProvider.getPackages("com.android.calendar", userId) : null;
        synchronized (this.mService.mPackages) {
            Intent intent;
            int i;
            Package installerPackage = getSystemPackageLPr(this.mService.mRequiredInstallerPackage);
            if (installerPackage != null && doesPackageSupportRuntimePermissions(installerPackage)) {
                grantRuntimePermissionsLPw(installerPackage, STORAGE_PERMISSIONS, true, userId);
            }
            Package verifierPackage = getSystemPackageLPr(this.mService.mRequiredVerifierPackage);
            if (verifierPackage != null && doesPackageSupportRuntimePermissions(verifierPackage)) {
                grantRuntimePermissionsLPw(verifierPackage, STORAGE_PERMISSIONS, true, userId);
                grantRuntimePermissionsLPw(verifierPackage, PHONE_PERMISSIONS, false, userId);
                grantRuntimePermissionsLPw(verifierPackage, SMS_PERMISSIONS, false, userId);
            }
            Package setupPackage = getSystemPackageLPr(this.mService.mSetupWizardPackage);
            boolean isSupportRuntimePmsPkg = false;
            if (setupPackage != null) {
                isSupportRuntimePmsPkg = doesPackageSupportRuntimePermissions(setupPackage);
            }
            if (HWFLOW) {
                Log.i(TAG, "grantDefaultSystemHandlerPermissions setupPackage =" + setupPackage + "doesPackageSupportRuntimePermissions(setupPackage) =" + isSupportRuntimePmsPkg);
            }
            if (isSupportRuntimePmsPkg) {
                grantRuntimePermissionsLPw(setupPackage, PHONE_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(setupPackage, CONTACTS_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(setupPackage, LOCATION_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(setupPackage, CAMERA_PERMISSIONS, userId);
            }
            Package cameraPackage = getDefaultSystemHandlerActivityPackageLPr(new Intent("android.media.action.IMAGE_CAPTURE"), userId);
            if (cameraPackage != null && doesPackageSupportRuntimePermissions(cameraPackage)) {
                grantRuntimePermissionsLPw(cameraPackage, CAMERA_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(cameraPackage, MICROPHONE_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(cameraPackage, STORAGE_PERMISSIONS, userId);
            }
            Package mediaStorePackage = getDefaultProviderAuthorityPackageLPr("media", userId);
            if (mediaStorePackage != null) {
                grantRuntimePermissionsLPw(mediaStorePackage, STORAGE_PERMISSIONS, true, userId);
            }
            Package downloadsPackage = getDefaultProviderAuthorityPackageLPr("downloads", userId);
            if (downloadsPackage != null) {
                grantRuntimePermissionsLPw(downloadsPackage, STORAGE_PERMISSIONS, true, userId);
            }
            Package downloadsUiPackage = getDefaultSystemHandlerActivityPackageLPr(new Intent("android.intent.action.VIEW_DOWNLOADS"), userId);
            if (downloadsUiPackage != null && doesPackageSupportRuntimePermissions(downloadsUiPackage)) {
                grantRuntimePermissionsLPw(downloadsUiPackage, STORAGE_PERMISSIONS, true, userId);
            }
            Package storagePackage = getDefaultProviderAuthorityPackageLPr("com.android.externalstorage.documents", userId);
            if (storagePackage != null) {
                grantRuntimePermissionsLPw(storagePackage, STORAGE_PERMISSIONS, true, userId);
            }
            Package certInstallerPackage = getDefaultSystemHandlerActivityPackageLPr(new Intent("android.credentials.INSTALL"), userId);
            if (certInstallerPackage != null && doesPackageSupportRuntimePermissions(certInstallerPackage)) {
                grantRuntimePermissionsLPw(certInstallerPackage, STORAGE_PERMISSIONS, true, userId);
            }
            Package dialerPackage;
            if (packages4 == null) {
                dialerPackage = getDefaultSystemHandlerActivityPackageLPr(new Intent("android.intent.action.DIAL"), userId);
                if (dialerPackage != null) {
                    grantDefaultPermissionsToDefaultSystemDialerAppLPr(dialerPackage, userId);
                }
            } else {
                for (String dialerAppPackageName : packages4) {
                    dialerPackage = getSystemPackageLPr(dialerAppPackageName);
                    if (dialerPackage != null) {
                        grantDefaultPermissionsToDefaultSystemDialerAppLPr(dialerPackage, userId);
                    }
                }
            }
            if (packages5 != null) {
                for (String simCallManagerPackageName : packages5) {
                    Package simCallManagerPackage = getSystemPackageLPr(simCallManagerPackageName);
                    if (simCallManagerPackage != null) {
                        grantDefaultPermissionsToDefaultSimCallManagerLPr(simCallManagerPackage, userId);
                    }
                }
            }
            Package smsPackage;
            if (packages3 == null) {
                intent = new Intent("android.intent.action.MAIN");
                intent.addCategory("android.intent.category.APP_MESSAGING");
                smsPackage = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
                if (smsPackage != null) {
                    grantDefaultPermissionsToDefaultSystemSmsAppLPr(smsPackage, userId);
                }
            } else {
                for (String smsPackageName : packages3) {
                    smsPackage = getSystemPackageLPr(smsPackageName);
                    if (smsPackage != null) {
                        grantDefaultPermissionsToDefaultSystemSmsAppLPr(smsPackage, userId);
                    }
                }
            }
            Package cbrPackage = getDefaultSystemHandlerActivityPackageLPr(new Intent("android.provider.Telephony.SMS_CB_RECEIVED"), userId);
            if (cbrPackage != null && doesPackageSupportRuntimePermissions(cbrPackage)) {
                grantRuntimePermissionsLPw(cbrPackage, SMS_PERMISSIONS, userId);
            }
            Package carrierProvPackage = getDefaultSystemHandlerServicePackageLPr(new Intent("android.provider.Telephony.SMS_CARRIER_PROVISION"), userId);
            if (carrierProvPackage != null && doesPackageSupportRuntimePermissions(carrierProvPackage)) {
                grantRuntimePermissionsLPw(carrierProvPackage, SMS_PERMISSIONS, false, userId);
            }
            Intent calendarIntent = new Intent("android.intent.action.MAIN");
            calendarIntent.addCategory("android.intent.category.APP_CALENDAR");
            Package calendarPackage = getDefaultSystemHandlerActivityPackageLPr(calendarIntent, userId);
            if (calendarPackage != null && doesPackageSupportRuntimePermissions(calendarPackage)) {
                grantRuntimePermissionsLPw(calendarPackage, CALENDAR_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(calendarPackage, CONTACTS_PERMISSIONS, userId);
            }
            Package calendarProviderPackage = getDefaultProviderAuthorityPackageLPr("com.android.calendar", userId);
            if (calendarProviderPackage != null) {
                grantRuntimePermissionsLPw(calendarProviderPackage, CONTACTS_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(calendarProviderPackage, CALENDAR_PERMISSIONS, true, userId);
                grantRuntimePermissionsLPw(calendarProviderPackage, STORAGE_PERMISSIONS, userId);
            }
            List<Package> calendarSyncAdapters = getHeadlessSyncAdapterPackagesLPr(packages7, userId);
            int calendarSyncAdapterCount = calendarSyncAdapters.size();
            for (i = 0; i < calendarSyncAdapterCount; i++) {
                Package calendarSyncAdapter = (Package) calendarSyncAdapters.get(i);
                if (doesPackageSupportRuntimePermissions(calendarSyncAdapter)) {
                    grantRuntimePermissionsLPw(calendarSyncAdapter, CALENDAR_PERMISSIONS, userId);
                }
            }
            intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.APP_CONTACTS");
            Package contactsPackage = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
            if (contactsPackage != null && doesPackageSupportRuntimePermissions(contactsPackage)) {
                grantRuntimePermissionsLPw(contactsPackage, CONTACTS_PERMISSIONS, userId);
                grantRuntimePermissionsLPw(contactsPackage, PHONE_PERMISSIONS, userId);
            }
            List<Package> contactsSyncAdapters = getHeadlessSyncAdapterPackagesLPr(packages6, userId);
            int contactsSyncAdapterCount = contactsSyncAdapters.size();
            for (i = 0; i < contactsSyncAdapterCount; i++) {
                Package contactsSyncAdapter = (Package) contactsSyncAdapters.get(i);
                if (doesPackageSupportRuntimePermissions(contactsSyncAdapter)) {
                    grantRuntimePermissionsLPw(contactsSyncAdapter, CONTACTS_PERMISSIONS, userId);
                }
            }
            Package contactsProviderPackage = getDefaultProviderAuthorityPackageLPr("com.android.contacts", userId);
            if (contactsProviderPackage != null) {
                grantRuntimePermissionsLPw(contactsProviderPackage, CONTACTS_PERMISSIONS, true, userId);
                grantRuntimePermissionsLPw(contactsProviderPackage, PHONE_PERMISSIONS, true, userId);
                grantRuntimePermissionsLPw(contactsProviderPackage, STORAGE_PERMISSIONS, userId);
            }
            Package deviceProvisionPackage = getDefaultSystemHandlerActivityPackageLPr(new Intent("android.app.action.PROVISION_MANAGED_DEVICE"), userId);
            if (deviceProvisionPackage != null && doesPackageSupportRuntimePermissions(deviceProvisionPackage)) {
                grantRuntimePermissionsLPw(deviceProvisionPackage, CONTACTS_PERMISSIONS, userId);
            }
            intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.APP_MAPS");
            Package mapsPackage = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
            if (mapsPackage != null && doesPackageSupportRuntimePermissions(mapsPackage)) {
                grantRuntimePermissionsLPw(mapsPackage, LOCATION_PERMISSIONS, userId);
            }
            intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.APP_GALLERY");
            Package galleryPackage = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
            if (galleryPackage != null && doesPackageSupportRuntimePermissions(galleryPackage)) {
                grantRuntimePermissionsLPw(galleryPackage, STORAGE_PERMISSIONS, userId);
            }
            intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.APP_EMAIL");
            Package emailPackage = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
            if (emailPackage != null && doesPackageSupportRuntimePermissions(emailPackage)) {
                grantRuntimePermissionsLPw(emailPackage, CONTACTS_PERMISSIONS, userId);
            }
            Package browserPackage = null;
            String defaultBrowserPackage = this.mService.getDefaultBrowserPackageName(userId);
            if (defaultBrowserPackage != null) {
                browserPackage = getPackageLPr(defaultBrowserPackage);
            }
            if (browserPackage == null) {
                Intent browserIntent = new Intent("android.intent.action.MAIN");
                browserIntent.addCategory("android.intent.category.APP_BROWSER");
                browserPackage = getDefaultSystemHandlerActivityPackageLPr(browserIntent, userId);
            }
            if (browserPackage != null && doesPackageSupportRuntimePermissions(browserPackage)) {
                grantRuntimePermissionsLPw(browserPackage, LOCATION_PERMISSIONS, userId);
            }
            if (packages != null) {
                for (String voiceInteractPackageName : packages) {
                    Package voiceInteractPackage = getSystemPackageLPr(voiceInteractPackageName);
                    if (voiceInteractPackage != null && doesPackageSupportRuntimePermissions(voiceInteractPackage)) {
                        grantRuntimePermissionsLPw(voiceInteractPackage, CONTACTS_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(voiceInteractPackage, CALENDAR_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(voiceInteractPackage, MICROPHONE_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(voiceInteractPackage, PHONE_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(voiceInteractPackage, SMS_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(voiceInteractPackage, LOCATION_PERMISSIONS, userId);
                    }
                }
            }
            intent = new Intent("android.speech.RecognitionService");
            intent.addCategory("android.intent.category.DEFAULT");
            Package voiceRecoPackage = getDefaultSystemHandlerServicePackageLPr(intent, userId);
            if (voiceRecoPackage != null && doesPackageSupportRuntimePermissions(voiceRecoPackage)) {
                grantRuntimePermissionsLPw(voiceRecoPackage, MICROPHONE_PERMISSIONS, userId);
            }
            if (packages2 != null) {
                for (String packageName : packages2) {
                    Package locationPackage = getSystemPackageLPr(packageName);
                    if (locationPackage != null && doesPackageSupportRuntimePermissions(locationPackage)) {
                        grantRuntimePermissionsLPw(locationPackage, CONTACTS_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(locationPackage, CALENDAR_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(locationPackage, MICROPHONE_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(locationPackage, PHONE_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(locationPackage, SMS_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(locationPackage, LOCATION_PERMISSIONS, true, userId);
                        grantRuntimePermissionsLPw(locationPackage, CAMERA_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(locationPackage, SENSORS_PERMISSIONS, userId);
                        grantRuntimePermissionsLPw(locationPackage, STORAGE_PERMISSIONS, userId);
                    }
                }
            }
            intent = new Intent("android.intent.action.VIEW");
            intent.addCategory("android.intent.category.DEFAULT");
            intent.setDataAndType(Uri.fromFile(new File("foo.mp3")), AUDIO_MIME_TYPE);
            Package musicPackage = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
            if (musicPackage != null && doesPackageSupportRuntimePermissions(musicPackage)) {
                grantRuntimePermissionsLPw(musicPackage, STORAGE_PERMISSIONS, userId);
            }
            if (this.mService.hasSystemFeature("android.hardware.type.watch", 0)) {
                intent = new Intent("android.intent.action.MAIN");
                intent.addCategory("android.intent.category.HOME_MAIN");
                Package wearHomePackage = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
                if (wearHomePackage != null && doesPackageSupportRuntimePermissions(wearHomePackage)) {
                    grantRuntimePermissionsLPw(wearHomePackage, CONTACTS_PERMISSIONS, false, userId);
                    grantRuntimePermissionsLPw(wearHomePackage, PHONE_PERMISSIONS, true, userId);
                    grantRuntimePermissionsLPw(wearHomePackage, MICROPHONE_PERMISSIONS, false, userId);
                    grantRuntimePermissionsLPw(wearHomePackage, LOCATION_PERMISSIONS, false, userId);
                }
            }
            Package printSpoolerPackage = getSystemPackageLPr("com.android.printspooler");
            if (printSpoolerPackage != null && doesPackageSupportRuntimePermissions(printSpoolerPackage)) {
                grantRuntimePermissionsLPw(printSpoolerPackage, LOCATION_PERMISSIONS, true, userId);
            }
            Package emergencyInfoPckg = getDefaultSystemHandlerActivityPackageLPr(new Intent("android.telephony.action.EMERGENCY_ASSISTANCE"), userId);
            if (emergencyInfoPckg != null && doesPackageSupportRuntimePermissions(emergencyInfoPckg)) {
                grantRuntimePermissionsLPw(emergencyInfoPckg, CONTACTS_PERMISSIONS, true, userId);
                grantRuntimePermissionsLPw(emergencyInfoPckg, PHONE_PERMISSIONS, true, userId);
            }
            intent = new Intent("android.intent.action.VIEW");
            intent.setType("vnd.android.cursor.item/ndef_msg");
            Package nfcTagPkg = getDefaultSystemHandlerActivityPackageLPr(intent, userId);
            if (nfcTagPkg != null && doesPackageSupportRuntimePermissions(nfcTagPkg)) {
                grantRuntimePermissionsLPw(nfcTagPkg, CONTACTS_PERMISSIONS, false, userId);
                grantRuntimePermissionsLPw(nfcTagPkg, PHONE_PERMISSIONS, false, userId);
            }
            this.mService.mSettings.onDefaultRuntimePermissionsGrantedLPr(userId);
        }
    }

    private void grantDefaultPermissionsToDefaultSystemDialerAppLPr(Package dialerPackage, int userId) {
        if (doesPackageSupportRuntimePermissions(dialerPackage)) {
            grantRuntimePermissionsLPw(dialerPackage, PHONE_PERMISSIONS, this.mService.hasSystemFeature("android.hardware.type.watch", 0), userId);
            grantRuntimePermissionsLPw(dialerPackage, CONTACTS_PERMISSIONS, userId);
            grantRuntimePermissionsLPw(dialerPackage, SMS_PERMISSIONS, userId);
            grantRuntimePermissionsLPw(dialerPackage, MICROPHONE_PERMISSIONS, userId);
        }
    }

    private void grantDefaultPermissionsToDefaultSystemSmsAppLPr(Package smsPackage, int userId) {
        if (doesPackageSupportRuntimePermissions(smsPackage)) {
            grantRuntimePermissionsLPw(smsPackage, PHONE_PERMISSIONS, userId);
            grantRuntimePermissionsLPw(smsPackage, CONTACTS_PERMISSIONS, userId);
            grantRuntimePermissionsLPw(smsPackage, SMS_PERMISSIONS, userId);
        }
    }

    public void grantDefaultPermissionsToDefaultSmsAppLPr(String packageName, int userId) {
        Log.i(TAG, "Granting permissions to default sms app for user:" + userId);
        if (packageName != null) {
            Package smsPackage = getPackageLPr(packageName);
            if (smsPackage != null && doesPackageSupportRuntimePermissions(smsPackage)) {
                grantRuntimePermissionsLPw(smsPackage, PHONE_PERMISSIONS, false, true, userId);
                grantRuntimePermissionsLPw(smsPackage, CONTACTS_PERMISSIONS, false, true, userId);
                grantRuntimePermissionsLPw(smsPackage, SMS_PERMISSIONS, false, true, userId);
            }
        }
    }

    public void grantDefaultPermissionsToDefaultDialerAppLPr(String packageName, int userId) {
        Log.i(TAG, "Granting permissions to default dialer app for user:" + userId);
        if (packageName != null) {
            Package dialerPackage = getPackageLPr(packageName);
            if (dialerPackage != null && doesPackageSupportRuntimePermissions(dialerPackage)) {
                grantRuntimePermissionsLPw(dialerPackage, PHONE_PERMISSIONS, false, true, userId);
                grantRuntimePermissionsLPw(dialerPackage, CONTACTS_PERMISSIONS, false, true, userId);
                grantRuntimePermissionsLPw(dialerPackage, SMS_PERMISSIONS, false, true, userId);
                grantRuntimePermissionsLPw(dialerPackage, MICROPHONE_PERMISSIONS, false, true, userId);
            }
        }
    }

    private void grantDefaultPermissionsToDefaultSimCallManagerLPr(Package simCallManagerPackage, int userId) {
        Log.i(TAG, "Granting permissions to sim call manager for user:" + userId);
        if (doesPackageSupportRuntimePermissions(simCallManagerPackage)) {
            grantRuntimePermissionsLPw(simCallManagerPackage, PHONE_PERMISSIONS, userId);
            grantRuntimePermissionsLPw(simCallManagerPackage, MICROPHONE_PERMISSIONS, userId);
        }
    }

    public void grantDefaultPermissionsToDefaultSimCallManagerLPr(String packageName, int userId) {
        if (packageName != null) {
            Package simCallManagerPackage = getPackageLPr(packageName);
            if (simCallManagerPackage != null) {
                grantDefaultPermissionsToDefaultSimCallManagerLPr(simCallManagerPackage, userId);
            }
        }
    }

    public void grantDefaultPermissionsToEnabledCarrierAppsLPr(String[] packageNames, int userId) {
        Log.i(TAG, "Granting permissions to enabled carrier apps for user:" + userId);
        if (packageNames != null) {
            for (String packageName : packageNames) {
                Package carrierPackage = getSystemPackageLPr(packageName);
                if (carrierPackage != null && doesPackageSupportRuntimePermissions(carrierPackage)) {
                    grantRuntimePermissionsLPw(carrierPackage, PHONE_PERMISSIONS, userId);
                    grantRuntimePermissionsLPw(carrierPackage, LOCATION_PERMISSIONS, userId);
                    grantRuntimePermissionsLPw(carrierPackage, SMS_PERMISSIONS, userId);
                }
            }
        }
    }

    public void grantDefaultPermissionsToDefaultBrowserLPr(String packageName, int userId) {
        Log.i(TAG, "Granting permissions to default browser for user:" + userId);
        if (packageName != null) {
            Package browserPackage = getSystemPackageLPr(packageName);
            if (browserPackage != null && doesPackageSupportRuntimePermissions(browserPackage)) {
                grantRuntimePermissionsLPw(browserPackage, LOCATION_PERMISSIONS, false, false, userId);
            }
        }
    }

    private Package getDefaultSystemHandlerActivityPackageLPr(Intent intent, int userId) {
        ResolveInfo handler = this.mService.resolveIntent(intent, intent.resolveType(this.mService.mContext.getContentResolver()), DEFAULT_FLAGS, userId);
        if (handler == null || handler.activityInfo == null) {
            return null;
        }
        ActivityInfo activityInfo = handler.activityInfo;
        if (activityInfo.packageName.equals(this.mService.mResolveActivity.packageName) && activityInfo.name.equals(this.mService.mResolveActivity.name)) {
            return null;
        }
        return getSystemPackageLPr(handler.activityInfo.packageName);
    }

    private Package getDefaultSystemHandlerServicePackageLPr(Intent intent, int userId) {
        List<ResolveInfo> handlers = this.mService.queryIntentServices(intent, intent.resolveType(this.mService.mContext.getContentResolver()), DEFAULT_FLAGS, userId).getList();
        if (handlers == null) {
            return null;
        }
        int handlerCount = handlers.size();
        for (int i = 0; i < handlerCount; i++) {
            Package handlerPackage = getSystemPackageLPr(((ResolveInfo) handlers.get(i)).serviceInfo.packageName);
            if (handlerPackage != null) {
                return handlerPackage;
            }
        }
        return null;
    }

    private List<Package> getHeadlessSyncAdapterPackagesLPr(String[] syncAdapterPackageNames, int userId) {
        List<Package> syncAdapterPackages = new ArrayList();
        Intent homeIntent = new Intent("android.intent.action.MAIN");
        homeIntent.addCategory("android.intent.category.LAUNCHER");
        for (String syncAdapterPackageName : syncAdapterPackageNames) {
            homeIntent.setPackage(syncAdapterPackageName);
            if (this.mService.resolveIntent(homeIntent, homeIntent.resolveType(this.mService.mContext.getContentResolver()), DEFAULT_FLAGS, userId) == null) {
                Package syncAdapterPackage = getSystemPackageLPr(syncAdapterPackageName);
                if (syncAdapterPackage != null) {
                    syncAdapterPackages.add(syncAdapterPackage);
                }
            }
        }
        return syncAdapterPackages;
    }

    private Package getDefaultProviderAuthorityPackageLPr(String authority, int userId) {
        ProviderInfo provider = this.mService.resolveContentProvider(authority, DEFAULT_FLAGS, userId);
        if (provider != null) {
            return getSystemPackageLPr(provider.packageName);
        }
        return null;
    }

    private Package getPackageLPr(String packageName) {
        return (Package) this.mService.mPackages.get(packageName);
    }

    protected Package getSystemPackageLPr(String packageName) {
        Package pkg = getPackageLPr(packageName);
        if (pkg == null || !pkg.isSystemApp()) {
            return null;
        }
        if (isSysComponentOrPersistentPlatformSignedPrivAppLPr(pkg)) {
            pkg = null;
        }
        return pkg;
    }

    private void grantRuntimePermissionsLPw(Package pkg, Set<String> permissions, int userId) {
        grantRuntimePermissionsLPw(pkg, permissions, false, false, userId);
    }

    protected void grantRuntimePermissionsLPw(Package pkg, Set<String> permissions, boolean systemFixed, int userId) {
        grantRuntimePermissionsLPw(pkg, permissions, systemFixed, false, userId);
    }

    private void grantRuntimePermissionsLPw(Package pkg, Set<String> permissions, boolean systemFixed, boolean isDefaultPhoneOrSms, int userId) {
        if (!pkg.requestedPermissions.isEmpty()) {
            List<String> requestedPermissions = pkg.requestedPermissions;
            Set set = null;
            if (!isDefaultPhoneOrSms && pkg.isUpdatedSystemApp()) {
                PackageSetting sysPs = this.mService.mSettings.getDisabledSystemPkgLPr(pkg.packageName);
                if (sysPs != null) {
                    if (sysPs.pkg == null || sysPs.pkg.requestedPermissions.isEmpty()) {
                        if (sysPs.pkg == null) {
                            Log.e(TAG, "Package Setting: " + sysPs.toString() + ", Code Path : " + sysPs.codePathString + ", First Install time: " + sysPs.firstInstallTime + ", Last update time: " + sysPs.lastUpdateTime + ", Installer Package Name: " + sysPs.getInstallerPackageName() + ", Real Name: " + sysPs.realName);
                            Log.wtf(TAG, "Disabled System Package: " + pkg.packageName + "is NULL!!!", new Throwable());
                        }
                        return;
                    }
                    if (!requestedPermissions.equals(sysPs.pkg.requestedPermissions)) {
                        set = new ArraySet(requestedPermissions);
                        requestedPermissions = sysPs.pkg.requestedPermissions;
                    }
                }
            }
            int grantablePermissionCount = requestedPermissions.size();
            for (int i = 0; i < grantablePermissionCount; i++) {
                String permission = (String) requestedPermissions.get(i);
                if ((set == null || set.contains(permission)) && permissions.contains(permission)) {
                    int flags = this.mService.getPermissionFlags(permission, pkg.packageName, userId);
                    if (flags == 0 || isDefaultPhoneOrSms) {
                        if ((flags & 20) == 0) {
                            this.mService.grantRuntimePermission(pkg.packageName, permission, userId);
                            int newFlags = 32;
                            if (systemFixed) {
                                newFlags = 48;
                            }
                            this.mService.updatePermissionFlags(permission, pkg.packageName, newFlags, newFlags, userId);
                        }
                    }
                    if (!((flags & 32) == 0 || (flags & 16) == 0 || systemFixed)) {
                        this.mService.updatePermissionFlags(permission, pkg.packageName, 16, 0, userId);
                    }
                }
            }
        }
    }

    private boolean isSysComponentOrPersistentPlatformSignedPrivAppLPr(Package pkg) {
        boolean z = true;
        if (UserHandle.getAppId(pkg.applicationInfo.uid) < 10000) {
            return true;
        }
        if (!pkg.isPrivilegedApp()) {
            return false;
        }
        PackageSetting sysPkg = this.mService.mSettings.getDisabledSystemPkgLPr(pkg.packageName);
        if (sysPkg == null || sysPkg.pkg == null || sysPkg.pkg.applicationInfo == null) {
            if ((pkg.applicationInfo.flags & 8) == 0) {
                return false;
            }
        } else if ((sysPkg.pkg.applicationInfo.flags & 8) == 0) {
            return false;
        }
        if (PackageManagerService.compareSignatures(this.mService.mPlatformPackage.mSignatures, pkg.mSignatures) != 0) {
            z = false;
        }
        return z;
    }

    private boolean isSysComponentOrPersistentPlatformSignedPrivApp(Package pkg) {
        boolean z = true;
        if (UserHandle.getAppId(pkg.applicationInfo.uid) < 10000) {
            return true;
        }
        if ((pkg.applicationInfo.privateFlags & 8) == 0 || (pkg.applicationInfo.flags & 8) == 0) {
            return false;
        }
        if (PackageManagerService.compareSignatures(this.mService.mPlatformPackage.mSignatures, pkg.mSignatures) != 0) {
            z = false;
        }
        return z;
    }

    private static boolean doesPackageSupportRuntimePermissions(Package pkg) {
        return pkg.applicationInfo.targetSdkVersion > 22;
    }
}
