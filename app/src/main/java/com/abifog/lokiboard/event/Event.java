/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.abifog.lokiboard.event;

import com.abifog.lokiboard.latin.common.Constants;
import com.abifog.lokiboard.latin.common.StringUtils;

/**
 * Class representing a generic input event as handled by Latin IME.
 *
 * This contains information about the origin of the event, but it is generalized and should
 * represent a software keypress, hardware keypress, or d-pad move alike.
 * Very importantly, this does not necessarily result in inputting one character, or even anything
 * at all - it may be a dead key, it may be a partial input, it may be a special key on the
 * keyboard, it may be a cancellation of a keypress (e.g. in a soft keyboard the finger of the
 * user has slid out of the key), etc. It may also be a batch input from a gesture or handwriting
 * for example.
 * The combiner should figure out what to do with this.
 */
public class Event {
    // Should the types below be represented by separate classes instead? It would be cleaner
    // but probably a bit too much
    // An event we don't handle in Latin IME, for example pressing Ctrl on a hardware keyboard.
    final public static int EVENT_TYPE_NOT_HANDLED = 0;
    // A key press that is part of input, for example pressing an alphabetic character on a
    // hardware qwerty keyboard. It may be part of a sequence that will be re-interpreted later
    // through combination.
    final public static int EVENT_TYPE_INPUT_KEYPRESS = 1;
    // A toggle event is triggered by a key that affects the previous character. An example would
    // be a numeric key on a 10-key keyboard, which would toggle between 1 - a - b - c with
    // repeated presses.
    final public static int EVENT_TYPE_TOGGLE = 2;
    // A mode event instructs the combiner to change modes. The canonical example would be the
    // hankaku/zenkaku key on a Japanese keyboard, or even the caps lock key on a qwerty keyboard
    // if handled at the combiner level.
    final public static int EVENT_TYPE_MODE_KEY = 3;
    // An event corresponding to a string generated by some software process.
    final public static int EVENT_TYPE_SOFTWARE_GENERATED_STRING = 6;
    // An event corresponding to a cursor move
    final public static int EVENT_TYPE_CURSOR_MOVE = 7;

    // 0 is a valid code point, so we use -1 here.
    final public static int NOT_A_CODE_POINT = -1;
    // -1 is a valid key code, so we use 0 here.
    final public static int NOT_A_KEY_CODE = 0;

    final private static int FLAG_NONE = 0;
    // This event is coming from a key repeat, software or hardware.
    final private static int FLAG_REPEAT = 0x2;
    // This event has already been consumed.
    final private static int FLAG_CONSUMED = 0x4;

    final private int mEventType; // The type of event - one of the constants above
    // The code point associated with the event, if relevant. This is a unicode code point, and
    // has nothing to do with other representations of the key. It is only relevant if this event
    // is of KEYPRESS type, but for a mode key like hankaku/zenkaku or ctrl, there is no code point
    // associated so this should be NOT_A_CODE_POINT to avoid unintentional use of its value when
    // it's not relevant.
    final public int mCodePoint;

    final public CharSequence mText;

    // The key code associated with the event, if relevant. This is relevant whenever this event
    // has been triggered by a key press, but not for a gesture for example. This has conceptually
    // no link to the code point, although keys that enter a straight code point may often set
    // this to be equal to mCodePoint for convenience. If this is not a key, this must contain
    // NOT_A_KEY_CODE.
    final public int mKeyCode;

    // Coordinates of the touch event, if relevant. If useful, we may want to replace this with
    // a MotionEvent or something in the future. This is only relevant when the keypress is from
    // a software keyboard obviously, unless there are touch-sensitive hardware keyboards in the
    // future or some other awesome sauce.
    final public int mX;
    final public int mY;

    // Some flags that can't go into the key code. It's a bit field of FLAG_*
    final private int mFlags;

    // The next event, if any. Null if there is no next event yet.
    final public Event mNextEvent;

    // This method is private - to create a new event, use one of the create* utility methods.
    private Event(final int type, final CharSequence text, final int codePoint, final int keyCode,
            final int x, final int y, final int flags,
            final Event next) {
        mEventType = type;
        mText = text;
        mCodePoint = codePoint;
        mKeyCode = keyCode;
        mX = x;
        mY = y;
        mFlags = flags;
        mNextEvent = next;
    }

    public static Event createSoftwareKeypressEvent(final int codePoint, final int keyCode,
            final int x, final int y, final boolean isKeyRepeat) {
        return new Event(EVENT_TYPE_INPUT_KEYPRESS, null, codePoint, keyCode, x, y,
                isKeyRepeat ? FLAG_REPEAT : FLAG_NONE, null);
    }

    /**
     * Creates an input event with a CharSequence. This is used by some software processes whose
     * output is a string, possibly with styling. Examples include press on a multi-character key,
     * or combination that outputs a string.
     * @param text the CharSequence associated with this event.
     * @param keyCode the key code, or NOT_A_KEYCODE if not applicable.
     * @return an event for this text.
     */
    public static Event createSoftwareTextEvent(final CharSequence text, final int keyCode) {
        return new Event(EVENT_TYPE_SOFTWARE_GENERATED_STRING, text, NOT_A_CODE_POINT, keyCode,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                FLAG_NONE, null /* next */);
    }

    // Returns whether this is a function key like backspace, ctrl, settings... as opposed to keys
    // that result in input like letters or space.
    public boolean isFunctionalKeyEvent() {
        // This logic may need to be refined in the future
        return NOT_A_CODE_POINT == mCodePoint;
    }

    public boolean isKeyRepeat() {
        return 0 != (FLAG_REPEAT & mFlags);
    }

    public boolean isConsumed() {
        return 0 != (FLAG_CONSUMED & mFlags);
    }

    public CharSequence getTextToCommit() {
        if (isConsumed()) {
            return ""; // A consumed event should input no text.
        }
        switch (mEventType) {
        case EVENT_TYPE_MODE_KEY:
        case EVENT_TYPE_NOT_HANDLED:
        case EVENT_TYPE_TOGGLE:
        case EVENT_TYPE_CURSOR_MOVE:
            return "";
        case EVENT_TYPE_INPUT_KEYPRESS:
            return StringUtils.newSingleCodePointString(mCodePoint);
        case EVENT_TYPE_SOFTWARE_GENERATED_STRING:
            return mText;
        }
        throw new RuntimeException("Unknown event type: " + mEventType);
    }
}
