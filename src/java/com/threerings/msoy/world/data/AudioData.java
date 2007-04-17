//
// $Id$

package com.threerings.msoy.world.data;

import com.samskivert.util.ObjectUtil;

import com.threerings.io.SimpleStreamableObject;

import com.threerings.msoy.item.data.all.Audio;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.item.data.all.MediaDesc;
import com.threerings.msoy.item.data.all.StaticMediaDesc;

/**
 * Contains information on background audio in a scene.
 */
public class AudioData extends SimpleStreamableObject
    implements Cloneable
{
    /** Default background audio media. */
    public static final MediaDesc defaultMedia =
        new StaticMediaDesc(MediaDesc.AUDIO_MPEG, Item.AUDIO, Item.MAIN_MEDIA);
    
    /** Identifies the id of the item that was used to create this. */
    public int itemId;

    /** Media contained in the audio item. */
    public MediaDesc media;

    /** Audio volume. */
    public float volume;

    /**
     * Constructor, populates the audio data object with default values.
     */
    public AudioData ()
    {
        itemId = 0;
        volume = 1.0f;
        media = defaultMedia;
    }
    
    /**
     * Helper function: specifies that this decor data structure has already been
     * populated from an Audio item object.
     */
    public boolean isInitialized ()
    {
        return itemId != 0;
    }
    
    // documentation inherited 
    @Override
    public boolean equals (Object other)
    {
        // compare audio item and volume
        if (other instanceof AudioData) {
            AudioData data = (AudioData) other;
            return data.itemId == this.itemId && data.volume == this.volume;
        }
        return false;
    }

    // documentation inherited
    @Override
    public String toString ()
    {
        return "Audio[itemId=" + itemId + ", media=" + media + "]";
    }

    // documentation inherited
    public Object clone ()
    {
        try {
            // perform a shallow copy of value attributes
            AudioData data = (AudioData) super.clone();
            return data;
        } catch (CloneNotSupportedException cnse) {
            throw new RuntimeException(cnse);           // not going to happen
        }
    }

}
