//
// $Id$

package com.threerings.msoy.world.data;

import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.world.client.RoomService;
import com.threerings.msoy.world.data.MemoryEntry;
import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.InvocationMarshaller;
import com.threerings.presents.dobj.InvocationResponseEvent;
import com.threerings.whirled.data.SceneUpdate;
import com.threerings.whirled.spot.data.Location;

/**
 * Provides the implementation of the {@link RoomService} interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
public class RoomMarshaller extends InvocationMarshaller
    implements RoomService
{
    /** The method id used to dispatch {@link #changeLocation} requests. */
    public static final int CHANGE_LOCATION = 1;

    // from interface RoomService
    public void changeLocation (Client arg1, ItemIdent arg2, Location arg3)
    {
        sendRequest(arg1, CHANGE_LOCATION, new Object[] {
            arg2, arg3
        });
    }

    /** The method id used to dispatch {@link #editRoom} requests. */
    public static final int EDIT_ROOM = 2;

    // from interface RoomService
    public void editRoom (Client arg1, InvocationService.ResultListener arg2)
    {
        InvocationMarshaller.ResultMarshaller listener2 = new InvocationMarshaller.ResultMarshaller();
        listener2.listener = arg2;
        sendRequest(arg1, EDIT_ROOM, new Object[] {
            listener2
        });
    }

    /** The method id used to dispatch {@link #requestControl} requests. */
    public static final int REQUEST_CONTROL = 3;

    // from interface RoomService
    public void requestControl (Client arg1, ItemIdent arg2)
    {
        sendRequest(arg1, REQUEST_CONTROL, new Object[] {
            arg2
        });
    }

    /** The method id used to dispatch {@link #sendSpriteMessage} requests. */
    public static final int SEND_SPRITE_MESSAGE = 4;

    // from interface RoomService
    public void sendSpriteMessage (Client arg1, ItemIdent arg2, String arg3, byte[] arg4, boolean arg5)
    {
        sendRequest(arg1, SEND_SPRITE_MESSAGE, new Object[] {
            arg2, arg3, arg4, Boolean.valueOf(arg5)
        });
    }

    /** The method id used to dispatch {@link #setActorState} requests. */
    public static final int SET_ACTOR_STATE = 5;

    // from interface RoomService
    public void setActorState (Client arg1, ItemIdent arg2, int arg3, String arg4)
    {
        sendRequest(arg1, SET_ACTOR_STATE, new Object[] {
            arg2, Integer.valueOf(arg3), arg4
        });
    }

    /** The method id used to dispatch {@link #updateMemory} requests. */
    public static final int UPDATE_MEMORY = 6;

    // from interface RoomService
    public void updateMemory (Client arg1, MemoryEntry arg2)
    {
        sendRequest(arg1, UPDATE_MEMORY, new Object[] {
            arg2
        });
    }

    /** The method id used to dispatch {@link #updateRoom} requests. */
    public static final int UPDATE_ROOM = 7;

    // from interface RoomService
    public void updateRoom (Client arg1, SceneUpdate[] arg2, InvocationService.InvocationListener arg3)
    {
        ListenerMarshaller listener3 = new ListenerMarshaller();
        listener3.listener = arg3;
        sendRequest(arg1, UPDATE_ROOM, new Object[] {
            arg2, listener3
        });
    }
}
