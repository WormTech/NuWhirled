//
// $Id$

package com.threerings.msoy.item.server.persist;

import com.samskivert.jdbc.depot.annotation.Entity;
import com.samskivert.jdbc.depot.annotation.Table;
import com.samskivert.jdbc.depot.annotation.TableGenerator;

import com.threerings.msoy.item.web.Item;
import com.threerings.msoy.item.web.MediaDesc;
import com.threerings.msoy.item.web.Photo;

/**
 * Represents an uploaded photograph for display in albums or for use as a
 * profile picture.
 */
@Entity
@Table
@TableGenerator(name="itemId", allocationSize=1, pkColumnValue="PHOTO")
public class PhotoRecord extends ItemRecord
{
    public static final int SCHEMA_VERSION = BASE_SCHEMA_VERSION*0x100 + 2;

    public static final String PHOTO_MEDIA_HASH = "photoMediaHash";
    public static final String PHOTO_MIME_TYPE = "photoMimeType";

    /** A hash code identifying the photo media. */
    public byte[] photoMediaHash;

    /** The MIME type of the {@link #photoMediaHash} media. */
    public byte photoMimeType;

    /** The size constraint on the {@link #photoMediaHash} media. */
    public byte photoConstraint;

    /** A caption for this photo (max length 255 characters). */
    public String caption;

    public PhotoRecord ()
    {
        super();
    }

    protected PhotoRecord (Photo photo)
    {
        super(photo);

        if (photo.photoMedia != null) {
            photoMediaHash = photo.photoMedia.hash;
            photoMimeType = photo.photoMedia.mimeType;
            photoConstraint = photo.photoMedia.constraint;
        }
        caption = photo.caption;
    }

    @Override // from ItemRecord
    public byte getType ()
    {
        return Item.PHOTO;
    }

    @Override
    protected Item createItem ()
    {
        Photo object = new Photo();
        object.photoMedia = photoMediaHash == null ? null :
            new MediaDesc(photoMediaHash, photoMimeType);
        object.caption = caption;
        return object;
    }
}
