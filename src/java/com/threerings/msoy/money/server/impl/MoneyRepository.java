//
// $Id$

package com.threerings.msoy.money.server.impl;

/**
 * Interface for retrieving and persisting entities in the money service.
 * 
 * @author Kyle Sampson <kyle@threerings.net>
 */
interface MoneyRepository
{
    /**
     * Retrieves a member's account info by member ID.
     */
    MemberAccountRecord getAccountById (int memberId);
    
    /**
     * Adds or updates the given account.
     * 
     * @param account Account to update.
     */
    void saveAccount (MemberAccountRecord account) throws StaleDataException;
    
    /**
     * Adds a history record for an account.
     * 
     * @param history History record to update.
     */
    void addHistory (MemberAccountHistoryRecord history);
}
