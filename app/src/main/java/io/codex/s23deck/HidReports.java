package io.codex.s23deck;

final class HidReports {
    static final int REPORT_KEYBOARD = 1;
    static final int REPORT_MOUSE = 2;
    static final int REPORT_CONSUMER = 3;

    static final byte MOD_CTRL = 0x01;
    static final byte MOD_SHIFT = 0x02;
    static final byte MOD_ALT = 0x04;
    static final byte MOD_GUI = 0x08;

    static final byte KEY_A = 0x04;
    static final byte KEY_B = 0x05;
    static final byte KEY_C = 0x06;
    static final byte KEY_D = 0x07;
    static final byte KEY_E = 0x08;
    static final byte KEY_F = 0x09;
    static final byte KEY_G = 0x0A;
    static final byte KEY_H = 0x0B;
    static final byte KEY_I = 0x0C;
    static final byte KEY_J = 0x0D;
    static final byte KEY_K = 0x0E;
    static final byte KEY_L = 0x0F;
    static final byte KEY_M = 0x10;
    static final byte KEY_N = 0x11;
    static final byte KEY_O = 0x12;
    static final byte KEY_P = 0x13;
    static final byte KEY_Q = 0x14;
    static final byte KEY_R = 0x15;
    static final byte KEY_S = 0x16;
    static final byte KEY_T = 0x17;
    static final byte KEY_U = 0x18;
    static final byte KEY_V = 0x19;
    static final byte KEY_W = 0x1A;
    static final byte KEY_X = 0x1B;
    static final byte KEY_Y = 0x1C;
    static final byte KEY_Z = 0x1D;
    static final byte KEY_1 = 0x1E;
    static final byte KEY_2 = 0x1F;
    static final byte KEY_3 = 0x20;
    static final byte KEY_4 = 0x21;
    static final byte KEY_5 = 0x22;
    static final byte KEY_6 = 0x23;
    static final byte KEY_7 = 0x24;
    static final byte KEY_8 = 0x25;
    static final byte KEY_9 = 0x26;
    static final byte KEY_0 = 0x27;
    static final byte KEY_ENTER = 0x28;
    static final byte KEY_ESC = 0x29;
    static final byte KEY_BACKSPACE = 0x2A;
    static final byte KEY_TAB = 0x2B;
    static final byte KEY_SPACE = 0x2C;
    static final byte KEY_MINUS = 0x2D;
    static final byte KEY_EQUAL = 0x2E;
    static final byte KEY_LEFT_BRACKET = 0x2F;
    static final byte KEY_RIGHT_BRACKET = 0x30;
    static final byte KEY_BACKSLASH = 0x31;
    static final byte KEY_SEMICOLON = 0x33;
    static final byte KEY_APOSTROPHE = 0x34;
    static final byte KEY_GRAVE = 0x35;
    static final byte KEY_COMMA = 0x36;
    static final byte KEY_DOT = 0x37;
    static final byte KEY_SLASH = 0x38;
    static final byte KEY_F3 = 0x3C;
    static final byte KEY_F4 = 0x3D;
    static final byte KEY_F11 = 0x44;
    static final byte KEY_F12 = 0x45;
    static final byte KEY_HOME = 0x4A;
    static final byte KEY_PAGE_UP = 0x4B;
    static final byte KEY_DELETE = 0x4C;
    static final byte KEY_END = 0x4D;
    static final byte KEY_PAGE_DOWN = 0x4E;
    static final byte KEY_RIGHT = 0x4F;
    static final byte KEY_LEFT = 0x50;
    static final byte KEY_DOWN = 0x51;
    static final byte KEY_UP = 0x52;

    static final int CONSUMER_SCAN_NEXT = 0x00B5;
    static final int CONSUMER_SCAN_PREVIOUS = 0x00B6;
    static final int CONSUMER_PLAY_PAUSE = 0x00CD;
    static final int CONSUMER_MUTE = 0x00E2;
    static final int CONSUMER_VOLUME_UP = 0x00E9;
    static final int CONSUMER_VOLUME_DOWN = 0x00EA;

    static final byte[] DESCRIPTOR = new byte[]{
            0x05, 0x01, 0x09, 0x06, (byte) 0xA1, 0x01,
            (byte) 0x85, REPORT_KEYBOARD,
            0x05, 0x07, 0x19, (byte) 0xE0, 0x29, (byte) 0xE7,
            0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, (byte) 0x95, 0x08, (byte) 0x81, 0x02,
            (byte) 0x95, 0x01, 0x75, 0x08, (byte) 0x81, 0x01,
            (byte) 0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, (byte) 0x81, 0x00,
            (byte) 0xC0,

            0x05, 0x01, 0x09, 0x02, (byte) 0xA1, 0x01,
            (byte) 0x85, REPORT_MOUSE,
            0x09, 0x01, (byte) 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x05,
            0x15, 0x00, 0x25, 0x01,
            (byte) 0x95, 0x05, 0x75, 0x01, (byte) 0x81, 0x02,
            (byte) 0x95, 0x01, 0x75, 0x03, (byte) 0x81, 0x03,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, (byte) 0x81, 0x25, 0x7F,
            0x75, 0x08, (byte) 0x95, 0x03, (byte) 0x81, 0x06,
            0x05, 0x0C, 0x0A, 0x38, 0x02,
            0x15, (byte) 0x81, 0x25, 0x7F,
            0x75, 0x08, (byte) 0x95, 0x01, (byte) 0x81, 0x06,
            (byte) 0xC0, (byte) 0xC0,

            0x05, 0x0C, 0x09, 0x01, (byte) 0xA1, 0x01,
            (byte) 0x85, REPORT_CONSUMER,
            0x15, 0x00, 0x26, (byte) 0xFF, 0x03,
            0x19, 0x00, 0x2A, (byte) 0xFF, 0x03,
            0x75, 0x10, (byte) 0x95, 0x01, (byte) 0x81, 0x00,
            (byte) 0xC0
    };

    private HidReports() {
    }
}
