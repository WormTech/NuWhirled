//
// $Id$

package com.threerings.msoy.comment.server;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import com.samskivert.depot.DatabaseException;
import com.samskivert.util.StringUtil;

import com.threerings.gwt.util.PagedResult;

import com.threerings.msoy.comment.gwt.Comment.CommentType;
import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.notify.server.NotificationManager;
import com.threerings.msoy.game.server.persist.GameInfoRecord;
import com.threerings.msoy.game.server.persist.MsoyGameRepository;

import com.threerings.msoy.item.server.ItemLogic;
import com.threerings.msoy.item.server.persist.CatalogRecord;
import com.threerings.msoy.item.server.persist.ItemRecord;
import com.threerings.msoy.item.server.persist.ItemRepository;

import com.threerings.msoy.comment.gwt.Comment;
import com.threerings.msoy.comment.gwt.CommentService;
import com.threerings.msoy.comment.server.persist.CommentRecord;
import com.threerings.msoy.comment.server.persist.CommentRepository;
import com.threerings.msoy.server.StatLogic;
import com.threerings.msoy.server.persist.MemberCardRecord;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.underwire.server.SupportLogic;
import com.threerings.msoy.person.gwt.FeedMessageType;
import com.threerings.msoy.person.server.FeedLogic;

import com.threerings.msoy.web.gwt.MemberCard;
import com.threerings.msoy.web.gwt.Pages;
import com.threerings.msoy.web.gwt.ServiceCodes;
import com.threerings.msoy.web.gwt.ServiceException;
import com.threerings.msoy.web.server.MsoyServiceServlet;

import com.threerings.msoy.room.data.MsoySceneModel;
import com.threerings.msoy.room.server.persist.MsoySceneRepository;
import com.threerings.msoy.room.server.persist.SceneRecord;

import static com.threerings.msoy.Log.log;

/**
 * Provides the server implementation of {@link CommentService}.
 */
public class CommentServlet extends MsoyServiceServlet
    implements CommentService
{
    // from interface CommentService
    public PagedResult<Comment> loadComments (
        CommentType etype, int eid, int offset, int count, boolean needCount)
        throws ServiceException
    {
        // no authentication required to view comments
        List<CommentRecord> records = _commentRepo.loadComments(etype.toByte(), eid, offset, count, false);

        // resolve the member cards for all commentors
        Set<Integer> memIds = Sets.newHashSet();
        for (CommentRecord record : records) {
            memIds.add(record.memberId);
        }
        Map<Integer, MemberCard> cards = MemberCardRecord.toMap(_memberRepo.loadMemberCards(memIds));

        // convert the comment records to runtime records
        List<Comment> comments = Lists.newArrayList();
        for (CommentRecord record : records) {
            Comment comment = record.toComment(cards);
            if (comment.commentor == null) {
                continue; // this member was deleted, shouldn't happen
            }
            comments.add(comment);
        }

        // prepare and deliver the final result
        PagedResult<Comment> result = new PagedResult<Comment>();
        result.page = comments;
        if (needCount) {
            result.total = (records.size() < count && offset == 0) ?
                records.size() : _commentRepo.loadCommentCount(etype.toByte(), eid);
        }

        return result;
    }

    // from interface CommentService
    public Comment postComment (CommentType etype, int eid, String text)
        throws ServiceException
    {
        MemberRecord mrec = requireValidatedUser();

        // validate the entity type and id (sort of; we can't *really* validate the id without a
        // bunch of entity specific befuckery which I don't particularly care to do)
        if (!etype.isValid() || eid == 0) {
            log.warning("Refusing to post comment on illegal entity", "type", etype, "id", eid,
                        "who", mrec.who(), "text",  StringUtil.truncate(text, 40, "..."));
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        // record the comment to the data-ma-base
        CommentRecord crec = _commentRepo.postComment(etype.toByte(), eid, mrec.memberId, text);

        // find out the owner id of and the entity name for the entity that was commented on
        int ownerId = 0;
        String entityName = null;

        // if this is a comment on a user room, post a self feed message
        if (etype.forRoom()) {
            SceneRecord scene = _sceneRepo.loadScene(eid);
            if (scene.ownerType == MsoySceneModel.OWNER_TYPE_MEMBER) {
                _feedLogic.publishSelfMessage(
                    scene.ownerId,  mrec.memberId, FeedMessageType.SELF_ROOM_COMMENT,
                    scene.sceneId, scene.name, MediaDesc.mdToString(scene.getSnapshotThumb()));
                ownerId = scene.ownerId;
                entityName = scene.name;
            }

        } else if (etype.forProfileWall()) {
            ownerId = eid;

        // comment on an item
        } else  if (etype.isItemType()) {
            try {
                ItemRepository<?> repo = _itemLogic.getRepository(etype.toItemType());
                CatalogRecord listing = repo.loadListing(eid, true);
                if (listing != null) {
                    ItemRecord item = listing.item;
                    if (item != null) {
                        ownerId = item.creatorId;
                        entityName = item.name;

                        // when commenting on a listed item, post a self feed message
                        _feedLogic.publishSelfMessage(
                            ownerId, mrec.memberId, FeedMessageType.SELF_ITEM_COMMENT,
                            item.getType().toByte(), listing.catalogId, item.name,
                            MediaDesc.mdToString(item.getThumbMediaDesc()));
                    }
                }

            } catch (ServiceException se) {
                // this is merely a failure to send the notification, don't let this exception
                // make it back to the commenter
                log.warning("Unable to locate repository for apparent item type", "type", etype);

            } catch (DatabaseException de) {
                log.warning("Unable to load comment target item", "type", etype, "id", eid, de);
            }
        } else if (etype.forGame()) {
            GameInfoRecord game = _msoyGameRepo.loadGame(eid);
            if (game != null) {
                ownerId = game.creatorId;
                entityName = game.name;
                _feedLogic.publishSelfMessage(
                    ownerId, mrec.memberId, FeedMessageType.SELF_GAME_COMMENT,
                    eid, game.name, MediaDesc.mdToString(game.getThumbMedia()));
            }
        }

        // notify the item creator that a comment was made
        if (ownerId > 0 && ownerId != mrec.memberId) {
            _notifyMan.notifyEntityCommented(ownerId, etype, eid, entityName);
        }

        // convert the record to a runtime record to return to the caller
        Comment comment = crec.toComment(Collections.<Integer, MemberCard>emptyMap());
        comment.commentor = mrec.getName();
        return comment;
    }

    // from interface CommentService
    public int rateComment (CommentType etype, int eid, long posted, boolean rating)
        throws ServiceException
    {
        MemberRecord mrec = requireValidatedUser();
        return _commentRepo.rateComment(etype.toByte(), eid, posted, mrec.memberId, rating);
    }

    // from interface CommentService
    public int deleteComments (CommentType etype, int eid, Collection<Long> stamps)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser();
        int deleted = 0;
        for (Long posted : stamps) {
            // if we're not support personnel, ensure that we are the poster of this comment
            if (!mrec.isSupport()) {
                CommentRecord record = _commentRepo.loadComment(etype.toByte(), eid, posted);
                if (record == null ||
                        !Comment.canDelete(etype, eid, record.memberId, mrec.memberId)) {
                    continue;
                }
            }
            _commentRepo.deleteComment(etype.toByte(), eid, posted);
            deleted ++;
        }
        return deleted;
    }

    // from interface CommentService
    public void complainComment (String subject, CommentType etype, int eid, long posted)
        throws ServiceException
    {
        MemberRecord mrec = requireValidatedUser();
        CommentRecord record = _commentRepo.loadComment(etype.toByte(), eid, posted);
        if (record == null) {
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        String link;
		if (etype.isItemType()) {
            link = Pages.SHOP.makeURL("l", etype.toByte(), eid);
        } else if (etype.forRoom()) {
            link = Pages.ROOMS.makeURL("room", eid);
        } else if (etype.forProfileWall()) {
            link = Pages.PEOPLE.makeURL(eid);
        } else if (etype.forGame()) {
            link = Pages.GAMES.makeURL("d", eid, "c");
        } else {
            link = null;
        }

        String text = "[" + new Date(posted) + "]\n" + record.text;
        _supportLogic.addMessageComplaint(
            mrec.getName(), record.memberId, text, subject, link);
    }

    // our dependencies
    @Inject protected CommentRepository _commentRepo;
    @Inject protected FeedLogic _feedLogic;
    @Inject protected ItemLogic _itemLogic;
    @Inject protected MsoyGameRepository _msoyGameRepo;
    @Inject protected MsoySceneRepository _sceneRepo;
    @Inject protected NotificationManager _notifyMan;
    @Inject protected StatLogic _statLogic;
    @Inject protected SupportLogic _supportLogic;
}
