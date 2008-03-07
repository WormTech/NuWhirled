//
// $Id$

package com.threerings.msoy.web.client;

import java.util.List;

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.user.client.rpc.RemoteService;

import com.threerings.msoy.fora.data.Issue;

import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.data.WebIdent;

/**
 * Defines issue related services available to the GWT client.
 */
public interface IssueService extends RemoteService
{
    /** Provides results for {@link #loadIssues}. */
    public static class IssueResult implements IsSerializable
    {
        /** The total count of issues. */
        public int issueCount;

        /** Returns true if we're able to manage issues. */
        public boolean isManager;

        /** The range of issues that were requested.
         * @gwt.typeArgs <com.threerings.msoy.fora.data.Issue> */
        public List issues;
    }

    /**
     * Loads issues of specific types, states.
     */
    public IssueResult loadIssues (
            WebIdent ident, int type, int state, int offset, int count, boolean needTotalCount)
        throws ServiceException;

    /**
     * Loads issues of specific types, states owned by the user.
     */
    public IssueResult loadOwnedIssues (
            WebIdent ident, int type, int state, int offset, int count, boolean needTotalCount)
        throws ServiceException;

    /**
     * Loads an issue from an issueId.
     */
    public Issue loadIssue (WebIdent ident, int issueId)
        throws ServiceException;

    /**
     * Loads a list of ForumMessage for an issueId.
     * @gwt.typeArgs <com.threerings.msoy.fora.data.ForumMessage>
     */
    public List loadMessages (WebIdent ident, int issueId)
        throws ServiceException;

    /**
     * Creates an issue.
     */
    public Issue createIssue (WebIdent ident, Issue issue, int messageId)
        throws ServiceException;

    /**
     * Updates an issue.
     */
    public void updateIssue (WebIdent ident, Issue issue)
        throws ServiceException;

    /**
     * Loads a list of possible issue owners.
     * @gwt.typeArgs <com.threerings.msoy.data.all.MemberName>
     */
    public List loadOwners (WebIdent ident)
        throws ServiceException;
}
