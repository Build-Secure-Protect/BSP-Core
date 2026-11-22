package com.mrgregles.bsp_core.looks;

import com.mrgregles.bsp_core.totem.TotemOwner;

import javax.annotation.Nullable;

/** What the LOOKS tab shows of a totem it is open on: a Shatter Totem, or a Demo Totem. */
public interface Dressable {
    /** The body look drawn now, or null for the plain gold body. */
    @Nullable
    TotemLook shownBody();

    /** The hat drawn now, or "" for none. */
    String shownHat();

    /** Whose skin MY HEAD shows on what is drawn now, or null for the viewer's own (a totem's tab is open only for its owner). */
    @Nullable
    default TotemOwner skinOwner() {
        return null;
    }
}
