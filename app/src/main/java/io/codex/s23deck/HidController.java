package io.codex.s23deck;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

final class HidController {
    interface Listener {
        void onStateChanged(String status);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            main.post(command);
        }
    };
    private final ExecutorService tx = Executors.newSingleThreadExecutor();
    private final BluetoothAdapter adapter;
    private final Listener listener;

    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private boolean appRegistered;
    private boolean profileOpening;
    private boolean appRegistering;
    private boolean closed;
    private int buttonMask;
    private String status = "Bluetooth idle";
    private final Object mouseLock = new Object();
    private int pendingDx;
    private int pendingDy;
    private int pendingWheel;
    private int pendingHorizontal;
    private boolean mouseFlushQueued;

    HidController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    void openProfile() {
        if (adapter == null) {
            setStatus("Bluetooth unavailable");
            return;
        }
        if (closed) {
            setStatus("Bluetooth closed");
            return;
        }
        if (hidDevice != null) {
            setStatus(appRegistered ? "HID registered" : "HID profile ready");
            if (!appRegistered && !appRegistering) {
                registerApp();
            }
            return;
        }
        if (profileOpening) {
            setStatus("Opening HID profile");
            return;
        }
        try {
            boolean ok = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE);
            profileOpening = ok;
            setStatus(ok ? "Opening HID profile" : "HID profile open failed");
        } catch (SecurityException e) {
            setStatus("Bluetooth permission missing");
        }
    }

    void close() {
        if (closed) {
            return;
        }
        releaseAllInputsNow();
        closed = true;
        tx.shutdownNow();
        try {
            if (hidDevice != null) {
                hidDevice.unregisterApp();
                adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice);
            }
        } catch (SecurityException ignored) {
            setStatus("Bluetooth permission missing");
        } catch (RuntimeException ignored) {
        }
        hidDevice = null;
        connectedDevice = null;
        appRegistered = false;
        appRegistering = false;
        profileOpening = false;
    }

    List<BluetoothDevice> bondedDevices() {
        if (adapter == null) {
            return Collections.emptyList();
        }
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            List<BluetoothDevice> devices = new ArrayList<>(bonded);
            Collections.sort(devices, new Comparator<BluetoothDevice>() {
                @Override
                public int compare(BluetoothDevice left, BluetoothDevice right) {
                    return deviceLabel(left).compareToIgnoreCase(deviceLabel(right));
                }
            });
            return devices;
        } catch (SecurityException e) {
            setStatus("Bluetooth permission missing");
            return Collections.emptyList();
        }
    }

    String currentStatus() {
        return status;
    }

    boolean isReady() {
        return hidDevice != null && appRegistered;
    }

    boolean isConnected() {
        return connectedDevice != null;
    }

    void registerApp() {
        if (hidDevice == null) {
            setStatus("HID profile not ready");
            return;
        }
        if (appRegistered) {
            setStatus("HID registered");
            return;
        }
        if (appRegistering) {
            setStatus("Registering HID app");
            return;
        }
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Codecks",
                "Phone trackpad, keyboard, and command deck",
                "Codex",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidReports.DESCRIPTOR
        );
        try {
            boolean ok = hidDevice.registerApp(sdp, null, null, mainExecutor, callback);
            appRegistering = ok;
            setStatus(ok ? "Registering HID app" : "HID registration failed");
        } catch (SecurityException e) {
            setStatus("Bluetooth permission missing");
        }
    }

    void connect(BluetoothDevice device) {
        if (!isReady()) {
            setStatus("Register HID first");
            return;
        }
        try {
            setStatus("Connecting " + deviceLabel(device));
            boolean ok = hidDevice.connect(device);
            if (!ok) {
                setStatus("Connect request failed");
            }
        } catch (SecurityException e) {
            setStatus("Bluetooth permission missing");
        }
    }

    void disconnect() {
        enqueue(new Runnable() {
            @Override
            public void run() {
                releaseAllInputsNow();
                BluetoothDevice device = connectedDevice;
                if (hidDevice == null || device == null) {
                    return;
                }
                try {
                    hidDevice.disconnect(device);
                } catch (SecurityException e) {
                    setStatus("Bluetooth permission missing");
                }
            }
        });
    }

    void sendMouse(final int dx, final int dy, final int wheel, final int horizontal) {
        synchronized (mouseLock) {
            pendingDx = boundedAdd(pendingDx, dx);
            pendingDy = boundedAdd(pendingDy, dy);
            pendingWheel = boundedAdd(pendingWheel, wheel);
            pendingHorizontal = boundedAdd(pendingHorizontal, horizontal);
            if (mouseFlushQueued) {
                return;
            }
            mouseFlushQueued = true;
        }
        enqueue(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int sx;
                    int sy;
                    int sw;
                    int sh;
                    synchronized (mouseLock) {
                        if (pendingDx == 0 && pendingDy == 0 && pendingWheel == 0 && pendingHorizontal == 0) {
                            mouseFlushQueued = false;
                            return;
                        }
                        sx = takeStep(pendingDx);
                        sy = takeStep(pendingDy);
                        sw = takeStep(pendingWheel);
                        sh = takeStep(pendingHorizontal);
                        pendingDx -= sx;
                        pendingDy -= sy;
                        pendingWheel -= sw;
                        pendingHorizontal -= sh;
                    }
                    sendReport(HidReports.REPORT_MOUSE, new byte[]{
                            (byte) buttonMask,
                            (byte) sx,
                            (byte) sy,
                            (byte) sw,
                            (byte) sh
                    });
                    nap(3);
                }
            }
        });
    }

    void setMouseButtons(final int newMask) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                buttonMask = newMask;
                sendReport(HidReports.REPORT_MOUSE, new byte[]{(byte) buttonMask, 0, 0, 0, 0});
            }
        });
    }

    void releaseAllInputs() {
        enqueue(new Runnable() {
            @Override
            public void run() {
                releaseAllInputsNow();
            }
        });
    }

    void click(final int mask) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                int previous = buttonMask;
                buttonMask = mask;
                sendReport(HidReports.REPORT_MOUSE, new byte[]{(byte) buttonMask, 0, 0, 0, 0});
                nap(28);
                buttonMask = previous;
                sendReport(HidReports.REPORT_MOUSE, new byte[]{(byte) buttonMask, 0, 0, 0, 0});
            }
        });
    }

    void keyTap(final byte modifier, final byte key) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                sendKeyboard(modifier, key);
                nap(16);
                releaseKeyboard();
            }
        });
    }

    void keyChord(final byte modifier, final byte first, final byte second) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                sendReport(HidReports.REPORT_KEYBOARD, new byte[]{modifier, 0, first, second, 0, 0, 0, 0});
                nap(18);
                releaseKeyboard();
            }
        });
    }

    void consumerTap(final int usage) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                sendReport(HidReports.REPORT_CONSUMER, new byte[]{
                        (byte) (usage & 0xFF),
                        (byte) ((usage >> 8) & 0xFF)
                });
                nap(20);
                sendReport(HidReports.REPORT_CONSUMER, new byte[]{0, 0});
            }
        });
    }

    void typeText(final String text) {
        enqueue(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < text.length(); i++) {
                    KeyStroke stroke = mapChar(text.charAt(i));
                    if (stroke == null) {
                        continue;
                    }
                    sendKeyboard(stroke.modifier, stroke.key);
                    nap(10);
                    releaseKeyboard();
                    nap(6);
                }
            }
        });
    }

    private void sendKeyboard(byte modifier, byte key) {
        sendReport(HidReports.REPORT_KEYBOARD, new byte[]{modifier, 0, key, 0, 0, 0, 0, 0});
    }

    private void releaseKeyboard() {
        sendReport(HidReports.REPORT_KEYBOARD, new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
    }

    private void releaseAllInputsNow() {
        synchronized (mouseLock) {
            pendingDx = 0;
            pendingDy = 0;
            pendingWheel = 0;
            pendingHorizontal = 0;
            mouseFlushQueued = false;
        }
        buttonMask = 0;
        sendReport(HidReports.REPORT_MOUSE, new byte[]{0, 0, 0, 0, 0});
        sendReport(HidReports.REPORT_KEYBOARD, new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
        sendReport(HidReports.REPORT_CONSUMER, new byte[]{0, 0});
    }

    private void enqueue(Runnable runnable) {
        if (closed) {
            return;
        }
        try {
            tx.execute(runnable);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void sendReport(int reportId, byte[] payload) {
        BluetoothHidDevice device = hidDevice;
        BluetoothDevice target = connectedDevice;
        if (device == null || target == null) {
            return;
        }
        try {
            device.sendReport(target, reportId, payload);
        } catch (SecurityException e) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    setStatus("Bluetooth permission missing");
                }
            });
        }
    }

    private void setStatus(String newStatus) {
        status = newStatus;
        if (listener != null) {
            listener.onStateChanged(newStatus);
        }
    }

    static String deviceLabel(BluetoothDevice device) {
        String name;
        try {
            name = device.getName();
        } catch (SecurityException e) {
            name = null;
        }
        if (name == null || name.trim().isEmpty()) {
            return device.getAddress();
        }
        return name + "  " + device.getAddress();
    }

    private static int takeStep(int value) {
        if (value > 127) {
            return 127;
        }
        if (value < -127) {
            return -127;
        }
        return value;
    }

    private static int boundedAdd(int left, int right) {
        long sum = (long) left + (long) right;
        if (sum > 8192) {
            return 8192;
        }
        if (sum < -8192) {
            return -8192;
        }
        return (int) sum;
    }

    private static void nap(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                profileOpening = false;
                hidDevice = (BluetoothHidDevice) proxy;
                setStatus("HID profile ready");
                registerApp();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                releaseAllInputsNow();
                profileOpening = false;
                hidDevice = null;
                appRegistered = false;
                appRegistering = false;
                connectedDevice = null;
                setStatus("HID profile closed");
            }
        }
    };

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            appRegistering = false;
            appRegistered = registered;
            if (registered) {
                setStatus("HID registered");
            } else {
                releaseAllInputsNow();
                connectedDevice = null;
                setStatus("HID unregistered");
            }
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device;
                setStatus("Connected " + deviceLabel(device));
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                setStatus("Connecting " + deviceLabel(device));
            } else if (state == BluetoothProfile.STATE_DISCONNECTING) {
                releaseAllInputsNow();
                setStatus("Disconnecting " + deviceLabel(device));
            } else {
                releaseAllInputsNow();
                if (device != null && device.equals(connectedDevice)) {
                    connectedDevice = null;
                }
                setStatus("Disconnected");
            }
        }
    };

    private static KeyStroke mapChar(char c) {
        if (c >= 'a' && c <= 'z') {
            return new KeyStroke((byte) 0, (byte) (HidReports.KEY_A + (c - 'a')));
        }
        if (c >= 'A' && c <= 'Z') {
            return new KeyStroke(HidReports.MOD_SHIFT, (byte) (HidReports.KEY_A + (c - 'A')));
        }
        if (c >= '1' && c <= '9') {
            return new KeyStroke((byte) 0, (byte) (HidReports.KEY_1 + (c - '1')));
        }
        switch (c) {
            case '0':
                return new KeyStroke((byte) 0, HidReports.KEY_0);
            case '\n':
            case '\r':
                return new KeyStroke((byte) 0, HidReports.KEY_ENTER);
            case '\t':
                return new KeyStroke((byte) 0, HidReports.KEY_TAB);
            case ' ':
                return new KeyStroke((byte) 0, HidReports.KEY_SPACE);
            case '-':
                return new KeyStroke((byte) 0, HidReports.KEY_MINUS);
            case '_':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_MINUS);
            case '=':
                return new KeyStroke((byte) 0, HidReports.KEY_EQUAL);
            case '+':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_EQUAL);
            case '[':
                return new KeyStroke((byte) 0, HidReports.KEY_LEFT_BRACKET);
            case '{':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_LEFT_BRACKET);
            case ']':
                return new KeyStroke((byte) 0, HidReports.KEY_RIGHT_BRACKET);
            case '}':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_RIGHT_BRACKET);
            case '\\':
                return new KeyStroke((byte) 0, HidReports.KEY_BACKSLASH);
            case '|':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_BACKSLASH);
            case ';':
                return new KeyStroke((byte) 0, HidReports.KEY_SEMICOLON);
            case ':':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_SEMICOLON);
            case '\'':
                return new KeyStroke((byte) 0, HidReports.KEY_APOSTROPHE);
            case '"':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_APOSTROPHE);
            case '`':
                return new KeyStroke((byte) 0, HidReports.KEY_GRAVE);
            case '~':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_GRAVE);
            case ',':
                return new KeyStroke((byte) 0, HidReports.KEY_COMMA);
            case '<':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_COMMA);
            case '.':
                return new KeyStroke((byte) 0, HidReports.KEY_DOT);
            case '>':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_DOT);
            case '/':
                return new KeyStroke((byte) 0, HidReports.KEY_SLASH);
            case '?':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_SLASH);
            case '!':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_1);
            case '@':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_2);
            case '#':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_3);
            case '$':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_4);
            case '%':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_5);
            case '^':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_6);
            case '&':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_7);
            case '*':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_8);
            case '(':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_9);
            case ')':
                return new KeyStroke(HidReports.MOD_SHIFT, HidReports.KEY_0);
            default:
                return null;
        }
    }

    private static final class KeyStroke {
        final byte modifier;
        final byte key;

        KeyStroke(byte modifier, byte key) {
            this.modifier = modifier;
            this.key = key;
        }
    }
}
