//
// $Id$

package com.threerings.msoy.game.data;

import com.threerings.presents.dobj.DSet;
import com.threerings.util.Name;

import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.TokenRing;

import com.whirled.data.GameData;

import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.MsoyTokenRing;
import com.threerings.msoy.data.MsoyUserObject;
import com.threerings.msoy.data.all.MemberName;

import com.threerings.msoy.item.data.all.Avatar;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.MediaDesc;

/**
 * Contains information on a player logged on to an MSOY Game server.
 */
public class PlayerObject extends BodyObject
    implements MsoyUserObject
{
    // AUTO-GENERATED: FIELDS START
    /** The field name of the <code>memberName</code> field. */
    public static final String MEMBER_NAME = "memberName";

    /** The field name of the <code>tokens</code> field. */
    public static final String TOKENS = "tokens";

    /** The field name of the <code>avatar</code> field. */
    public static final String AVATAR = "avatar";

    /** The field name of the <code>humanity</code> field. */
    public static final String HUMANITY = "humanity";

    /** The field name of the <code>gameState</code> field. */
    public static final String GAME_STATE = "gameState";

    /** The field name of the <code>questState</code> field. */
    public static final String QUEST_STATE = "questState";

    /** The field name of the <code>gameContent</code> field. */
    public static final String GAME_CONTENT = "gameContent";
    // AUTO-GENERATED: FIELDS END

    /** The name and id information for this user. */
    public MemberName memberName;

    /** The tokens defining the access controls for this user. */
    public MsoyTokenRing tokens;

    /** The avatar that the user has chosen, or null for guests. */
    public Avatar avatar;

    /** Our current assessment of how likely to be human this member is, in [0, {@link
     * MsoyCodes#MAX_HUMANITY}]. */
    public int humanity;

    /** Game state entries for the world game we're currently on. */
    public DSet<GameState> gameState = new DSet<GameState>();

    /** The quests of our current world game that we're currently on. */
    public DSet<QuestState> questState = new DSet<QuestState>();

    /** Contains information on player's ownership of game content (populated lazily). */
    public DSet<GameContentOwnership> gameContent = new DSet<GameContentOwnership>();

    /**
     * Return true if this user is merely a guest.
     */
    public boolean isGuest ()
    {
        return (getMemberId() == MemberName.GUEST_ID);
    }

    /**
     * Get the media to use as our headshot.
     */
    public MediaDesc getHeadShotMedia ()
    {
        if (avatar != null) {
            return avatar.getThumbnailMedia();
        }
        return Avatar.getDefaultThumbnailMediaFor(Item.AVATAR);
    }

    /**
     * Returns true if content is resolved for the specified game, false if it is not yet ready.
     */
    public boolean isContentResolved (int gameId)
    {
        return ownsGameContent(gameId, GameData.RESOLVED_MARKER, "");
    }

    /**
     * Returns true if this player owns the specified piece of game content. <em>Note:</em> the
     * content must have previously been resolved, which happens when the player enters the game in
     * question.
     */
    public boolean ownsGameContent (int gameId, byte type, String ident)
    {
        GameContentOwnership key = new GameContentOwnership();
        key.gameId = gameId;
        key.type = type;
        key.ident = ident;
        return gameContent.containsKey(key);
    }

    // from interface MsoyUserObject
    public MemberName getMemberName ()
    {
        return memberName;
    }

    // from interface MsoyUserObject
    public int getMemberId ()
    {
        return (memberName == null) ? MemberName.GUEST_ID : memberName.getMemberId();
    }

    // from interface MsoyUserObject
    public float getHumanity ()
    {
        return humanity / (float)MsoyCodes.MAX_HUMANITY;
    }

    @Override // from BodyObject
    public TokenRing getTokens ()
    {
        return tokens;
    }

    @Override // from BodyObject
    public Name getVisibleName ()
    {
        return memberName;
    }

    // AUTO-GENERATED: METHODS START
    /**
     * Requests that the <code>memberName</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setMemberName (MemberName value)
    {
        MemberName ovalue = this.memberName;
        requestAttributeChange(
            MEMBER_NAME, value, ovalue);
        this.memberName = value;
    }

    /**
     * Requests that the <code>tokens</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setTokens (MsoyTokenRing value)
    {
        MsoyTokenRing ovalue = this.tokens;
        requestAttributeChange(
            TOKENS, value, ovalue);
        this.tokens = value;
    }

    /**
     * Requests that the <code>avatar</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setAvatar (Avatar value)
    {
        Avatar ovalue = this.avatar;
        requestAttributeChange(
            AVATAR, value, ovalue);
        this.avatar = value;
    }

    /**
     * Requests that the <code>humanity</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setHumanity (int value)
    {
        int ovalue = this.humanity;
        requestAttributeChange(
            HUMANITY, Integer.valueOf(value), Integer.valueOf(ovalue));
        this.humanity = value;
    }

    /**
     * Requests that the specified entry be added to the
     * <code>gameState</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void addToGameState (GameState elem)
    {
        requestEntryAdd(GAME_STATE, gameState, elem);
    }

    /**
     * Requests that the entry matching the supplied key be removed from
     * the <code>gameState</code> set. The set will not change until the
     * event is actually propagated through the system.
     */
    public void removeFromGameState (Comparable key)
    {
        requestEntryRemove(GAME_STATE, gameState, key);
    }

    /**
     * Requests that the specified entry be updated in the
     * <code>gameState</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void updateGameState (GameState elem)
    {
        requestEntryUpdate(GAME_STATE, gameState, elem);
    }

    /**
     * Requests that the <code>gameState</code> field be set to the
     * specified value. Generally one only adds, updates and removes
     * entries of a distributed set, but certain situations call for a
     * complete replacement of the set value. The local value will be
     * updated immediately and an event will be propagated through the
     * system to notify all listeners that the attribute did
     * change. Proxied copies of this object (on clients) will apply the
     * value change when they received the attribute changed notification.
     */
    public void setGameState (DSet<com.threerings.msoy.game.data.GameState> value)
    {
        requestAttributeChange(GAME_STATE, value, this.gameState);
        @SuppressWarnings("unchecked") DSet<com.threerings.msoy.game.data.GameState> clone =
            (value == null) ? null : value.typedClone();
        this.gameState = clone;
    }

    /**
     * Requests that the specified entry be added to the
     * <code>questState</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void addToQuestState (QuestState elem)
    {
        requestEntryAdd(QUEST_STATE, questState, elem);
    }

    /**
     * Requests that the entry matching the supplied key be removed from
     * the <code>questState</code> set. The set will not change until the
     * event is actually propagated through the system.
     */
    public void removeFromQuestState (Comparable key)
    {
        requestEntryRemove(QUEST_STATE, questState, key);
    }

    /**
     * Requests that the specified entry be updated in the
     * <code>questState</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void updateQuestState (QuestState elem)
    {
        requestEntryUpdate(QUEST_STATE, questState, elem);
    }

    /**
     * Requests that the <code>questState</code> field be set to the
     * specified value. Generally one only adds, updates and removes
     * entries of a distributed set, but certain situations call for a
     * complete replacement of the set value. The local value will be
     * updated immediately and an event will be propagated through the
     * system to notify all listeners that the attribute did
     * change. Proxied copies of this object (on clients) will apply the
     * value change when they received the attribute changed notification.
     */
    public void setQuestState (DSet<com.threerings.msoy.game.data.QuestState> value)
    {
        requestAttributeChange(QUEST_STATE, value, this.questState);
        @SuppressWarnings("unchecked") DSet<com.threerings.msoy.game.data.QuestState> clone =
            (value == null) ? null : value.typedClone();
        this.questState = clone;
    }

    /**
     * Requests that the specified entry be added to the
     * <code>gameContent</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void addToGameContent (GameContentOwnership elem)
    {
        requestEntryAdd(GAME_CONTENT, gameContent, elem);
    }

    /**
     * Requests that the entry matching the supplied key be removed from
     * the <code>gameContent</code> set. The set will not change until the
     * event is actually propagated through the system.
     */
    public void removeFromGameContent (Comparable key)
    {
        requestEntryRemove(GAME_CONTENT, gameContent, key);
    }

    /**
     * Requests that the specified entry be updated in the
     * <code>gameContent</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void updateGameContent (GameContentOwnership elem)
    {
        requestEntryUpdate(GAME_CONTENT, gameContent, elem);
    }

    /**
     * Requests that the <code>gameContent</code> field be set to the
     * specified value. Generally one only adds, updates and removes
     * entries of a distributed set, but certain situations call for a
     * complete replacement of the set value. The local value will be
     * updated immediately and an event will be propagated through the
     * system to notify all listeners that the attribute did
     * change. Proxied copies of this object (on clients) will apply the
     * value change when they received the attribute changed notification.
     */
    public void setGameContent (DSet<com.threerings.msoy.game.data.GameContentOwnership> value)
    {
        requestAttributeChange(GAME_CONTENT, value, this.gameContent);
        @SuppressWarnings("unchecked") DSet<com.threerings.msoy.game.data.GameContentOwnership> clone =
            (value == null) ? null : value.typedClone();
        this.gameContent = clone;
    }
    // AUTO-GENERATED: METHODS END
}
