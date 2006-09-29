//
// $Id$

package com.threerings.msoy.client;

import com.threerings.util.Name;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;

/**
 * Services for members.
 */
public interface MemberService extends InvocationService
{
    /**
     * Request to add the specified user to the client's friendlist.
     */
    public void alterFriend (
        Client client, int friendId, boolean add,
        InvocationListener listener);

    /**
     * Request to know the home scene id for the specified friend.
     */
    public void getMemberHomeId (
        Client client, int memberId, ResultListener listener);

    /**
     * Set the avatar in use by this user.
     */
    public void setAvatar (
        Client client, int avatarId, InvocationListener listener);

    /**
     * Set the display name for this user.
     */
    public void setDisplayName (
        Client client, String name, InvocationListener listener);
}
