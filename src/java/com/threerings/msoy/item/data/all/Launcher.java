//
// $Id$

package com.threerings.msoy.item.data.all;

import com.threerings.orth.data.MediaDesc;

/**
 * Contains the runtime data for a Launcher item.
 */
public class Launcher extends GameItem
{
    /** Indicates whether the game we're launching is an AVRG. */
    public boolean isAVRG;

    @Override // from Item
    public MsoyItemType getType ()
    {
        return MsoyItemType.LAUNCHER;
    }

    @Override // from Item
    public MediaDesc getPreviewMedia ()
    {
        return getFurniMedia();
    }
}
