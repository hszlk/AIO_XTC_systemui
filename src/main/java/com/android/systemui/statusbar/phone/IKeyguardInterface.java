package com.android.systemui.statusbar.phone;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Hand-written equivalent of the vendor AIDL interface
 * {@code com.android.systemui.IKeyguardInterface}.
 *
 * <p>Why hand-written instead of AIDL: this rebuild has no {@code aidl} step in
 * its build pipeline, and the wire protocol is trivially simple (two booleans,
 * one void call, and two {@link IBinder} strong references - no custom
 * Parcelable). This file reproduces <b>exactly</b> the bytes the AIDL compiler
 * would have generated, because the reference launcher
 * ({@code com.xtc.i3launcher}, {@code com.xtc.systemui.keyguard.KeyguardService})
 * talks to us over the raw binder protocol:</p>
 *
 * <ul>
 *   <li>interface descriptor: {@value #DESCRIPTOR} (must stay byte-identical -
 *       the peer uses {@code queryLocalInterface(DESCRIPTOR)});</li>
 *   <li>transaction codes 1..5 exactly as decompiled from the vendor
 *       {@code classes.dex};</li>
 *   <li>code {@code 0x5F4E5446} ({@code '_N' 'T' 'F'} = {@code INTERFACE_TRANSACTION})
 *       returns the descriptor string.</li>
 * </ul>
 *
 * <p>Semantics used by SystemUI (see {@link KeyguardController}):
 * {@code isKeyguardLocked}/{@code isKeyguardSecure} are queries whose failure
 * path folds to {@code true} (be conservative: treat as locked / secure);
 * {@code requestDismissKeyguard} asks the external launcher to unlock and the
 * actual continuation runs from the {@link IKeyguardCallbackInterface}
 * {@code onKeyguardDismissed} callback; {@code register/unregisterCallback}
 * attach/detach the callback binder.</p>
 */
public interface IKeyguardInterface extends IInterface {

    /** Binder descriptor - MUST match the vendor AIDL declaration exactly. */
    String DESCRIPTOR = "com.android.systemui.IKeyguardInterface";

    int TRANSACTION_IS_KEYGUARD_LOCKED = 1;
    int TRANSACTION_IS_KEYGUARD_SECURE = 2;
    int TRANSACTION_REQUEST_DISMISS_KEYGUARD = 3;
    int TRANSACTION_REGISTER_CALLBACK = 4;
    int TRANSACTION_UNREGISTER_CALLBACK = 5;

    /** AIDL generated constant {@code ('_' << 24) | ('N' << 16) | ('T' << 8) | 'F'}. */
    int INTERFACE_TRANSACTION = 0x5F4E5446;

    boolean isKeyguardLocked() throws RemoteException;

    boolean isKeyguardSecure() throws RemoteException;

    void requestDismissKeyguard() throws RemoteException;

    void registerCallback(IKeyguardCallbackInterface callback) throws RemoteException;

    void unregisterCallback(IKeyguardCallbackInterface callback) throws RemoteException;

    /** Binder stub skeleton matching the generated AIDL {@code Stub}. */
    abstract class Stub extends Binder implements IKeyguardInterface {

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        /**
         * Casts an incoming binder to the interface, returning a local
         * implementation when it lives in this process and a proxy otherwise.
         */
        public static IKeyguardInterface asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IKeyguardInterface) {
                return (IKeyguardInterface) local;
            }
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case TRANSACTION_IS_KEYGUARD_LOCKED: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean locked = isKeyguardLocked();
                    reply.writeNoException();
                    reply.writeInt(locked ? 1 : 0);
                    return true;
                }
                case TRANSACTION_IS_KEYGUARD_SECURE: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean secure = isKeyguardSecure();
                    reply.writeNoException();
                    reply.writeInt(secure ? 1 : 0);
                    return true;
                }
                case TRANSACTION_REQUEST_DISMISS_KEYGUARD: {
                    data.enforceInterface(DESCRIPTOR);
                    requestDismissKeyguard();
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_REGISTER_CALLBACK: {
                    data.enforceInterface(DESCRIPTOR);
                    registerCallback(IKeyguardCallbackInterface.Stub.asInterface(
                            data.readStrongBinder()));
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_UNREGISTER_CALLBACK: {
                    data.enforceInterface(DESCRIPTOR);
                    unregisterCallback(IKeyguardCallbackInterface.Stub.asInterface(
                            data.readStrongBinder()));
                    reply.writeNoException();
                    return true;
                }
                case IKeyguardInterface.INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        /** Client-side proxy - identical logic to the AIDL generated Proxy. */
        private static final class Proxy implements IKeyguardInterface {

            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public boolean isKeyguardLocked() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (!mRemote.transact(TRANSACTION_IS_KEYGUARD_LOCKED, data, reply, 0)) {
                        throw new RemoteException("isKeyguardLocked: transaction rejected");
                    }
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public boolean isKeyguardSecure() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (!mRemote.transact(TRANSACTION_IS_KEYGUARD_SECURE, data, reply, 0)) {
                        throw new RemoteException("isKeyguardSecure: transaction rejected");
                    }
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void requestDismissKeyguard() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (!mRemote.transact(TRANSACTION_REQUEST_DISMISS_KEYGUARD, data, reply, 0)) {
                        throw new RemoteException("requestDismissKeyguard: transaction rejected");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void registerCallback(IKeyguardCallbackInterface callback)
                    throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(callback != null ? callback.asBinder() : null);
                    if (!mRemote.transact(TRANSACTION_REGISTER_CALLBACK, data, reply, 0)) {
                        throw new RemoteException("registerCallback: transaction rejected");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void unregisterCallback(IKeyguardCallbackInterface callback)
                    throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(callback != null ? callback.asBinder() : null);
                    if (!mRemote.transact(TRANSACTION_UNREGISTER_CALLBACK, data, reply, 0)) {
                        throw new RemoteException("unregisterCallback: transaction rejected");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public String toString() {
                return "IKeyguardInterface.Proxy[" + DESCRIPTOR + "]";
            }
        }
    }
}
