package moe.shizuku.api;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.Objects;

import moe.shizuku.server.IShizukuService;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

public class ShizukuService {

    private static IBinder binder;
    private static IShizukuService service;

    protected static void setBinder(@Nullable IBinder binder) {
        if (ShizukuService.binder == binder) return;

        if (binder == null) {
            ShizukuService.binder = null;
            ShizukuService.service = null;
        } else {
            ShizukuService.binder = binder;
            ShizukuService.service = IShizukuService.Stub.asInterface(binder);

            try {
                ShizukuService.binder.linkToDeath(ShizukuProvider.DEATH_RECIPIENT, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    @NonNull
    private static IShizukuService requireService() {
        if (service == null) {
            throw new IllegalStateException("binder haven't been received");
        }
        return service;
    }

    @Nullable
    public static IBinder getBinder() {
        return binder;
    }

    public static boolean pingBinder() {
        return binder != null && binder.pingBinder();
    }

    /**
     * Used by manager only
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static void exit() throws RemoteException {
        requireService().exit();
    }

    /**
     * Used by manager only
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static void sendUserService(@NonNull IBinder binder, @NonNull Bundle options) throws RemoteException {
        requireService().sendUserService(binder, options);
    }

    /**
     * Call {@link IBinder#transact(int, Parcel, Parcel, int)} at remote service.
     *
     * <p>How to construct the data parcel:
     * <code><br>data.writeInterfaceToken(ShizukuApiConstants.BINDER_DESCRIPTOR);
     * <br>data.writeStrongBinder(\/* binder you want to use at remote *\/);
     * <br>data.writeInt(\/* transact code you want to use *\/);
     * <br>data.writeInterfaceToken(\/* interface name of that binder *\/);
     * <br>\/* write data of the binder call you want*\/</code>
     *
     * @see SystemServiceHelper#obtainParcel(String, String, String)
     * @see SystemServiceHelper#obtainParcel(String, String, String, String)
     */
    public static void transactRemote(@NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        requireService().asBinder().transact(ShizukuApiConstants.BINDER_TRANSACTION_transact, data, reply, flags);
    }

    /**
     * Start a new process at remote service, parameters are passed to {@link java.lang.Runtime#exec(String, String[], java.io.File)}.
     * <p>
     * Note, you may need to read/write streams from RemoteProcess in different threads.
     * </p>
     *
     * @return RemoteProcess holds the binder of remote process
     * @deprecated This method is super easy to be abused, it may be removed in the future.
     * Currently the only known use is install packages, but use binder in much more easier (see sample).
     */
    @Deprecated
    public static RemoteProcess newProcess(@NonNull String[] cmd, @Nullable String[] env, @Nullable String dir) throws RemoteException {
        return new RemoteProcess(requireService().newProcess(cmd, env, dir));
    }

    /**
     * Returns uid of remote service.
     *
     * @return uid
     */
    public static int getUid() throws RemoteException {
        return requireService().getUid();
    }

    /**
     * Returns remote service version.
     *
     * @return server version
     */
    public static int getVersion() throws RemoteException {
        return requireService().getVersion();
    }

    /**
     * Return latest service version when this library was released.
     *
     * @return Latest service version
     * @see ShizukuService#getVersion()
     */
    public static int getLatestServiceVersion() {
        return ShizukuApiConstants.SERVER_VERSION;
    }

    /**
     * Check permission at remote service.
     *
     * @param permission permission name
     * @return PackageManager.PERMISSION_DENIED or PackageManager.PERMISSION_GRANTED
     */
    public static int checkPermission(String permission) throws RemoteException {
        return requireService().checkPermission(permission);
    }

    /**
     * Returns SELinux context of Shizuku server process.
     *
     * <p>This API is only meaningful for root app using {@link ShizukuService#newProcess(String[], String[], String)}.</p>
     *
     * <p>For adb, context should always be <code>u:r:shell:s0</code>.
     * <br>For root, context depends on su the user uses. E.g., context of Magisk is <code>u:r:magisk:s0</code>.
     * If the user's su does not allow binder calls between su and app, Shizuku will switch to context <code>u:r:shell:s0</code>.
     * </p>
     *
     * @return SELinux context
     * @since added from version 6
     */
    public static String getSELinuxContext() throws RemoteException {
        return requireService().getSELinuxContext();
    }

    public static class UserServiceOptionsBuilder {

        private final String id;
        private String className;
        private Integer versionCode;
        private Boolean alwaysRecreate;
        private boolean useMainProcess = false;
        private String processNameSuffix;
        private boolean debuggable = false;

        public UserServiceOptionsBuilder(@NonNull String id) {
            this.id = id;
        }

        public UserServiceOptionsBuilder setClassName(String className) {
            this.className = className;
            return this;
        }

        public UserServiceOptionsBuilder setVersionCode(int versionCode) {
            this.versionCode = versionCode;
            return this;
        }

        public UserServiceOptionsBuilder setAlwaysRecreate(boolean alwaysRecreate) {
            this.alwaysRecreate = alwaysRecreate;
            return this;
        }

        public UserServiceOptionsBuilder useMainProcess() {
            this.useMainProcess = true;
            return this;
        }

        public UserServiceOptionsBuilder useStandaloneProcess(String processNameSuffix, boolean debuggable) {
            this.useMainProcess = false;
            this.debuggable = debuggable;
            this.processNameSuffix = processNameSuffix;
            return this;
        }

        public Bundle build() {
            if (!useMainProcess) {
                Objects.requireNonNull(processNameSuffix, "process name suffix must not be null");
            }

            Bundle options = new Bundle();
            options.putString(ShizukuApiConstants.USER_SERVICE_ARG_ID, Objects.requireNonNull(id, "id must not be null"));
            options.putString(ShizukuApiConstants.USER_SERVICE_ARG_CLASSNAME, Objects.requireNonNull(className, "classname must not be null"));
            options.putBoolean(ShizukuApiConstants.USER_SERVICE_ARG_DEBUGGABLE, debuggable);
            if (versionCode != null) {
                options.putInt(ShizukuApiConstants.USER_SERVICE_ARG_VERSION_CODE, versionCode);
            }
            if (alwaysRecreate != null) {
                options.putBoolean(ShizukuApiConstants.USER_SERVICE_ARG_ALWAYS_RECREATE, alwaysRecreate);
            }
            if (!useMainProcess && !TextUtils.isEmpty(processNameSuffix)) {
                options.putString(ShizukuApiConstants.USER_SERVICE_ARG_PROCESS_NAME, processNameSuffix);
            }
            return options;
        }
    }

    /**
     * Run service class from user apk in Shizuku server process.
     *
     * @return IBinder user service binder
     * @since added from version 10
     */
    public static IBinder addUserService(@NonNull Context context, @NonNull Bundle options) throws RemoteException {
        options.putString(ShizukuApiConstants.USER_SERVICE_ARG_PACKAGE_NAME, context.getPackageName());
        return requireService().addUserService(options);
    }

    /**
     * Remove user class.
     *
     * @param id id
     * @return removed
     */
    public static boolean removeUserService(@NonNull Context context, @NonNull String id) throws RemoteException {
        Bundle options = new Bundle();
        options.putString(ShizukuApiConstants.USER_SERVICE_ARG_ID, Objects.requireNonNull(id, "id must not be null"));
        options.putString(ShizukuApiConstants.USER_SERVICE_ARG_PACKAGE_NAME, context.getPackageName());
        return requireService().removeUserService(options);
    }
}