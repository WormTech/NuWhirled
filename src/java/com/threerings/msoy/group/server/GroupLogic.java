//
// $Id: GroupServlet.java 10518 2008-08-07 22:29:12Z mdb $

package com.threerings.msoy.group.server;

import static com.threerings.msoy.Log.log;

import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.samskivert.io.PersistenceException;
import com.samskivert.jdbc.DuplicateKeyException;
import com.threerings.msoy.data.StatType;
import com.threerings.msoy.group.data.all.Group;
import com.threerings.msoy.group.data.all.GroupMembership;
import com.threerings.msoy.group.gwt.GroupCodes;
import com.threerings.msoy.group.gwt.GroupExtras;
import com.threerings.msoy.group.server.persist.GroupMembershipRecord;
import com.threerings.msoy.group.server.persist.GroupRecord;
import com.threerings.msoy.group.server.persist.GroupRepository;
import com.threerings.msoy.server.MemberNodeActions;
import com.threerings.msoy.server.StatLogic;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.web.data.ServiceCodes;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.presents.annotation.BlockingThread;

/**
 * Contains group related services used by servlets and other blocking thread code.
 */
@BlockingThread @Singleton
public class GroupLogic
{

    /**
     * Create a new group
     */
    public Group createGroup (MemberRecord mrec, Group group, GroupExtras extras)
        throws ServiceException
    {
        // make sure the name is valid; this is checked on the client as well
        if (!isValidName(group.name)) {
            log.warning("Asked to create group with invalid name [for=" + mrec.who() +
                    ", name=" + group.name + "].");
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        try {
            final GroupRecord grec = new GroupRecord();
            grec.name = group.name;
            grec.blurb = group.blurb;
            grec.policy = group.policy;
            if (group.logo != null) {
                grec.logoMimeType = group.logo.mimeType;
                grec.logoMediaHash = group.logo.hash;
                grec.logoMediaConstraint = group.logo.constraint;
            }
            grec.homepageUrl = extras.homepageUrl;
            grec.charter = extras.charter;
            grec.catalogItemType = extras.catalogItemType;
            grec.catalogTag = extras.catalogTag;

            // we fill this in ourselves
            grec.creatorId = mrec.memberId;

            // create the group and then add the creator to it
            _groupRepo.createGroup(grec, extras.game);
            _groupRepo.joinGroup(grec.groupId, grec.creatorId, GroupMembership.RANK_MANAGER);

            // if the creator is online, update their runtime data
            GroupMembership gm = new GroupMembership();
            gm.group = grec.toGroupName();
            gm.rank = GroupMembership.RANK_MANAGER;
            MemberNodeActions.joinedGroup(grec.creatorId, gm);

            // update player stats
            _statLogic.incrementStat(mrec.memberId, StatType.WHIRLEDS_CREATED, 1);

            return grec.toGroupObject();

        } catch (DuplicateKeyException dke) {
            throw new ServiceException(GroupCodes.E_GROUP_NAME_IN_USE);

        } catch (PersistenceException pe) {
            log.warning("Failed to create group [for=" + mrec.who() +
                    ", group=" + group + ", extras=" + extras + "].", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }


    // from interface GroupService
    public void updateGroup (MemberRecord mrec, Group group, GroupExtras extras)
        throws ServiceException
    {
        // make sure the name is valid; this is checked on the client as well
        if (!isValidName(group.name)) {
            log.warning("Asked to update group with invalid name [for=" + mrec.who() +
                    ", name=" + group.name + "].");
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        try {
            GroupMembershipRecord gmrec = _groupRepo.getMembership(group.groupId, mrec.memberId);
            if (gmrec == null || gmrec.rank != GroupMembership.RANK_MANAGER) {
                log.warning("in updateGroup, invalid permissions");
                throw new ServiceException("m.invalid_permissions");
            }

            GroupRecord grec = _groupRepo.loadGroup(group.groupId);
            if (grec == null) {
                throw new PersistenceException("Group not found [id=" + group.groupId + "]");
            }
            Map<String, Object> updates = grec.findUpdates(group, extras);
            if (updates.size() > 0) {
                _groupRepo.updateGroup(group.groupId, updates);
            }

        } catch (DuplicateKeyException dke) {
            throw new ServiceException(GroupCodes.E_GROUP_NAME_IN_USE);

        } catch (PersistenceException pe) {
            log.warning("updateGroup failed [group=" + group +
                    ", extras=" + extras + "]", pe);
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }
    }

    protected static boolean isValidName (String name)
    {
        return Character.isLetter(name.charAt(0)) || Character.isDigit(name.charAt(0));
    }

    // our dependencies
    @Inject protected StatLogic _statLogic;
    @Inject protected GroupRepository _groupRepo;
}
