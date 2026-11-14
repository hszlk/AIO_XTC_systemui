package com.android.systemui.statusbar.phone;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Hand-written equivalent of the vendor AIDL interface
 * {@code com.android.systemui.IKeyguardCallbackInterface}.
 *
 * <p>Direction: the vendor launcher's {@code KeyguardService} (the
 * {@link IKeyguardInterface} server) calls back into SystemUI with this
 * interface. {@link KeyguardController} is the (only) implementation.</p>
 *
 * <ul>
 *   <li>descriptor: {@value #DESCRIPTOR};</li>
 *   <li>transaction codes: 1 = {@code onKeyguardDismissed()},
 *       2 = {@code onKeyguardShown()} (exactly as decompiled);</li>
 *   <li>{@code INTERFACE_TRANSACTION} returns the descriptor.</li>
 * </ul>
 */
public interface IKeyguardCallbackInterface extends IInterface {

    /** Binder descriptor - MUST match the vendor AIDL declaration exactly. */
    String DESCRIPTOR = "com.android.systemui.IKeyguardCallbackInterface";

    int TRANSACTION_ON_KEYGUARD_DISMISSED = 1;
    int TRANSACTION_ON_KEYGUARD_SHOWN = 2;

    /** AIDL generated constant {@code ('_' << 24) | ('N' << 16) | ('T' << 8) | 'F'}. */
    int INTERFACE_TRANSACTION = 0x5F4E5446;

    void onKeyguardDismissed() throws RemoteException;

    void onKeyguardShown() throws RemoteException;

    /** Binder stub skeleton matching the generated AIDL {@code Stub}. */
    abstract class Stub extends Binder implements IKeyguardCallbackInterface {

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IKeyguardCallbackInterface asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IKeyguardCallbackInterface) {
                return (IKeyguardCallbackInterface) local;
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
                case TRANSACTION_ON_KEYGUARD_DISMISSED: {
                    data.enforceInterface(DESCRIPTOR);
                    onKeyguardDismissed();
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_ON_KEYGUARD_SHOWN: {
                    data.enforceInterface(DESCRIPTOR);
                    onKeyguardShown();
                    reply.writeNoException();
                    return true;
                }
                case IKeyguardCallbackInterface.INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        /** Client-side proxy - identical logic to the AIDL generated Proxy. */
        private static final class Proxy implements IKeyguardCallbackInterface {

            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public void onKeyguardDismissed() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (!mRemote.transact(TRANSACTION_ON_KEYGUARD_DISMISSED, data, reply, 0)) {
                        throw new RemoteException("onKeyguardDismissed: transaction rejected");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void onKeyguardShown() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (!mRemote.transact(TRANSACTION_ON_KEYGUARD_SHOWN, data, reply, 0)) {
                        throw new RemoteException("onKeyguardShown: transaction rejected");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public String toString() {
                return "IKeyguardCallbackInterface.Proxy[" + DESCRIPTOR + "]";
            }
        }
    }
}
