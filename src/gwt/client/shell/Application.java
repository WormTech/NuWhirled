//
// $Id$

package client.shell;

import java.util.HashMap;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.HistoryListener;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;

import com.threerings.msoy.web.client.MemberService;
import com.threerings.msoy.web.client.MemberServiceAsync;
import com.threerings.msoy.web.client.WebUserService;
import com.threerings.msoy.web.client.WebUserServiceAsync;
import com.threerings.msoy.web.data.WebCreds;

/**
 * Our main application and entry point. This dispatches a requests to the appropriate {@link
 * Page}. Some day it may also do fancy on-demand loading of JavaScript.
 */
public class Application
    implements EntryPoint, HistoryListener
{
    /**
     * Returns a {@link Hyperlink} that displays the details of a given group.
     */
    public static Hyperlink groupViewLink (String label, int groupId)
    {
        return createLink(label, "group", ""+groupId);
    }

    /**
     * Returns a {@link Hyperlink} that displays the details of a given member.
     */
    public static Hyperlink memberViewLink (String label, int memberId)
    {
        return createLink(label, "profile", ""+memberId);
    }

    /**
     * Returns a {@link Hyperlink} that navigates to the specified application page with the
     * specified arguments. A page should use this method to pass itself arguments.
     */
    public static Hyperlink createLink (String label, String page, String args)
    {
        return new Hyperlink(label, createLinkToken(page, args)) {
            public void setText (String text) {
                DOM.setInnerText(DOM.getChild(getElement(), 0), text);
            }
        };
    }

    /**
     * Returns HTML that links to the specified page with the specified arguments.
     */
    public static String createLinkHtml (String label, String page, String args)
    {
        return "<a href=\"#" + createLinkToken(page, args) + "\">" + label + "</a>";
    }

    /**
     * Returns a string that can be appended to '#' to link to the specified page with the
     * specified arguments.
     */
    public static String createLinkToken (String page, String args)
    {
        String token = page;
        if (args != null && args.length() > 0) {
            token = token + "-" + args;
        }
        return token;
    }

    // from interface EntryPoint
    public void onModuleLoad ()
    {
        // create our static page mappings (we can't load classes by name in wacky JavaScript land
        // so we have to hardcode the mappings)
        createMappings();

        // initialize our top-level context references
        initContext();

        // set up the callbackd that our flash clients can call
        configureCallbacks(this);

        // create our status/logon panel
        RootPanel.get("status").add(_status = new StatusPanel(this));

        // create our standard navigation panel
        RootPanel.get("navigation").add(_navi = new NaviPanel(_status));

        // initialize the status panel
        _status.init();

        // wire ourselves up to the history-based navigation mechanism
        History.addHistoryListener(this);

        // now wait for our status panel to call didLogon() or didLogoff()
    }

    // from interface HistoryListener
    public void onHistoryChanged (String token)
    {
        String page = (token == null || token.equals("")) ? "world" : token;
        String args = "";
        int semidx = token.indexOf("-");
        if (semidx != -1) {
            page = token.substring(0, semidx);
            args = token.substring(semidx+1);
        }
        displayPage(page, args);
    }

    protected void initContext ()
    {
        // wire up our remote services
        CShell.usersvc = (WebUserServiceAsync)GWT.create(WebUserService.class);
        ((ServiceDefTarget)CShell.usersvc).setServiceEntryPoint("/usersvc");
        CShell.membersvc = (MemberServiceAsync)GWT.create(MemberService.class);
        ((ServiceDefTarget)CShell.membersvc).setServiceEntryPoint("/membersvc");

        // load up our translation dictionaries
        CShell.cmsgs = (ShellMessages)GWT.create(ShellMessages.class);
        CShell.dmsgs = (DynamicMessages)GWT.create(DynamicMessages.class);
        CShell.smsgs = (ServerMessages)GWT.create(ServerMessages.class);
    }

    protected void displayPage (String ident, String args)
    {
        CShell.log("Displaying [page=" + ident + ", args=" + args + "].");

        // replace the page if necessary
        if (_page == null || !_page.getPageId().equals(ident)) {
            // tell any existing page that it's being unloaded
            if (_page != null) {
                _page.onPageUnload();
                _page = null;
            }

            // locate the creator for this page
            Page.Creator creator = (Page.Creator)_creators.get(ident);
            if (creator == null) {
                RootPanel.get("content").clear();
                RootPanel.get("content").add(new Label("Unknown page requested '" + ident + "'."));
                return;
            }

            // create the entry point and fire it up
            _page = creator.createPage();
            _page.init();
            _page.onPageLoad();
        }

        // now tell the page about its arguments
        _page.onHistoryChanged(args);
    }

    /**
     * Called when the player logs on (or when our session is validated).
     *
     * @return true if we need a headless header Flash client, false if the page is providing a
     * Flash client for us.
     */
    protected boolean didLogon (WebCreds creds)
    {
        CShell.creds = creds;
        _navi.didLogon(creds);
        if (_page == null) {
            // we can now load our starting page
            onHistoryChanged(History.getToken());
        } else {
            _page.didLogon(creds);
        }
        return false;
    }

    /**
     * Called when the player logs off.
     */
    protected void didLogoff ()
    {
        CShell.creds = null;
        _navi.didLogoff();
        if (_page == null) {
            // we can now load our starting page
            onHistoryChanged(History.getToken());
        } else {
            _page.didLogoff();
        }
    }

    /**
     * Called when the flash client has logged on.
     */
    protected void didLogonFromFlash (String displayName, int memberId, String token)
    {
        _status.validateSession(token);
    }

    /**
     * Called when our flow, gold or other "levels" have changed.
     */
    protected void levelsUpdated ()
    {
        _status.refreshLevels();
    }

    /**
     * Called when our mail notification status has changed.
     */
    protected void mailNotificationUpdated ()
    {
        _status.refreshMailNotification();
    }

    protected void createMappings ()
    {
        _creators.put("admin", client.admin.index.getCreator());
        _creators.put("catalog", client.catalog.index.getCreator());
        _creators.put("game", client.game.index.getCreator());
        _creators.put("group", client.group.index.getCreator());
        _creators.put("inventory", client.inventory.index.getCreator());
        _creators.put("mail", client.mail.index.getCreator());
        _creators.put("profile", client.profile.index.getCreator());
        _creators.put("swiftly", client.swiftly.index.getCreator());
        _creators.put("world", client.world.index.getCreator());
    }

    /**
     * Configures top-level functions that can be called by Flash when it wants to tell us about
     * things.
     */
    protected static native void configureCallbacks (Application app) /*-{
       $wnd.flashDidLogon = function (displayName, memberId, token) {
           app.@client.shell.Application::didLogonFromFlash(Ljava/lang/String;ILjava/lang/String;)(displayName, memberId, token);
       };
       $wnd.levelsUpdated = function () {
           app.@client.shell.Application::levelsUpdated()();
       };
       $wnd.mailNotificationUpdated = function () {
           app.@client.shell.Application::mailNotificationUpdated()();
       };
       $wnd.onunload = function (event) {
           var client = $doc.getElementById("asclient");
           if (client) {
               client.onUnload();
           }
           return true;
       };
       $wnd.helloWhirled = function () {
            return true;
       }
    }-*/;

    protected Page _page;
    protected HashMap _creators = new HashMap();

    protected NaviPanel _navi;
    protected StatusPanel _status;
}
