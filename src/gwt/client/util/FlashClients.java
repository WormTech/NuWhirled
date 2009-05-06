//
// $Id$

package client.util;

import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.data.all.UberClientModes;

import client.shell.CShell;
import client.shell.Frame;

/**
 * Utility methods for generating flash clients.
 */
public class FlashClients
{
    /**
     * Create a tiny applet for uploading media.
     */
    public static HTML createUploader (String mediaIds, String filetypes)
    {
        String flashVars = "auth=" + URL.encodeComponent(CShell.getAuthToken()) +
            "&mediaIds=" + URL.encodeComponent(mediaIds) +
            "&filetypes=" + URL.encodeComponent(filetypes);
        return WidgetUtil.createTransparentFlashContainer("uploader",
            "/clients/" + DeploymentConfig.version + "/uploader.swf", 200, 40, flashVars);
    }

    /**
     * Create a tiny applet that simply detects if the user has a camera and shows
     * a button if so.
     */
    public static HTML createCameraButton (String mediaIds)
    {
        String flashVars = "mediaIds=" + URL.encodeComponent(mediaIds);
        return WidgetUtil.createTransparentFlashContainer("camerabutton",
            "/clients/" + DeploymentConfig.version + "/camerabutton.swf", 160, 19, flashVars);
    }

    /**
     * Create a video player.
     *
     * @param path may be null to create an empty player that can be provided with
     *        video information later.
     */
    public static HTML createVideoPlayer (int width, int height, String path)
    {
        String flashVars = (path == null) ? null : "video=" + URL.encodeComponent(path);
        return WidgetUtil.createFlashContainer("videoPlayer",
            "/clients/" + DeploymentConfig.version + "/videoplayer.swf", width, height, flashVars);
    }

    /**
     * Create an audio player.
     */
    public static HTML createAudioPlayer (int width, int height, String path)
    {
        String flashVars = "audio=" + URL.encodeComponent(path);
        return WidgetUtil.createFlashContainer("audioPlayer",
            "/clients/" + DeploymentConfig.version + "/audioplayer.swf", width, height, flashVars);
    }

    /**
     * Create the image editor swf.
     *
     * @param currentURL may be null
     * @param maxWidth or -1 to allow any width 
     * @param maxHeight or -1 to allow any height
     * @param maxRequired whether the maxes are  maximums, or a _required_ size.
     */
    public static HTML createImageEditor (
        int width, int height, String mediaIds, boolean takeSnapshot, String currentURL,
        int maxWidth, int maxHeight, boolean maxRequired)
    {
        String flashVars = "auth=" + URL.encodeComponent(CShell.getAuthToken()) +
            "&mediaIds=" + URL.encodeComponent(mediaIds);
        if (takeSnapshot) {
            flashVars += "&takeSnapshot=true";
        }
        if (currentURL != null) {
            flashVars += "&url=" + URL.encodeComponent(currentURL);
        }
        if (maxWidth > 0 && maxHeight > 0) {
            String prefix = maxRequired ? "req" : "max";
            flashVars += "&" + prefix + "Width=" + maxWidth +
                "&" + prefix + "Height=" + maxHeight;
        }
        return WidgetUtil.createFlashContainer("imageEditor",
            "/clients/" + DeploymentConfig.version + "/imageeditor.swf",
            width, height, flashVars);
    }

    /**
     * Creates a world client, and embeds it in a container object, with which it can communicate
     * via the Flash/Javascript interface.
     */
    public static void embedWorldClient (Panel container, String flashVars)
    {
        if (shouldShowFlash(container, 0, 0)) {
            Widget embed = WidgetUtil.embedFlashObject(
                container, WidgetUtil.createFlashObjectDefinition(
                    "asclient", "/clients/" + DeploymentConfig.version + "/world-client.swf",
                    "100%", getClientHeight(), flashVars));
            embed.setHeight("100%");
        }
    }

    /**
     * Creates a game client, and embeds it in a container object, with which it can communicate
     * via the Flash/Javascript interface.
     */
    public static void embedGameClient (Panel container, String flashVars)
    {
        if (shouldShowFlash(container, 0, 0)) {
            WidgetUtil.embedFlashObject(
                container, WidgetUtil.createFlashObjectDefinition(
                    "asclient", "/clients/" + DeploymentConfig.version + "/game-client.swf",
                    "100%", getClientHeight(), flashVars));
        }
    }

    /**
     * Creates a featured places world client, and embeds it in the container object.
     */
    public static void embedFeaturedPlaceView (Panel container, String flashVars)
    {
        if (shouldShowFlash(container, FEATURED_PLACE_WIDTH, FEATURED_PLACE_HEIGHT)) {
            WidgetUtil.embedFlashObject(
                container, WidgetUtil.createFlashObjectDefinition(
                    "featuredplace", "/clients/" + DeploymentConfig.version + "/world-client.swf",
                    FEATURED_PLACE_WIDTH, FEATURED_PLACE_HEIGHT, flashVars));
        }
    }

    /**
     * Creates a decor viewer, and embeds it in the supplied HTML object which *must* be already
     * added to the DOM.
     */
    public static void embedDecorViewer (HTML html)
    {
        // see if we need to emit a warning instead
        String definition = CShell.frame.checkFlashVersion(600, 400);
        if (definition != null) {
            html.setHTML(definition);
            return;
        }

        html.setHTML(WidgetUtil.createFlashObjectDefinition("decorViewer",
            "/clients/" + DeploymentConfig.version + "/world-client.swf", 600, 400,
            "mode=" + UberClientModes.DECOR_EDITOR + "&username=Tester"));
    }

    /**
     * Creates a neighborhood view definition, as an object definition string. The resulting
     * string can be turned into an embedded Flash object using a call to
     * WidgetUtil.embedFlashObject or equivalent.
     */
    public static String createPopularPlacesDefinition (String hotspotData)
    {
        String definition = CShell.frame.checkFlashVersion(0,0);
        return definition != null ? definition : WidgetUtil.createFlashObjectDefinition(
            "hotspots", "/clients/" + DeploymentConfig.version + "/neighborhood.swf",
            "100%", String.valueOf(Frame.CLIENT_HEIGHT - BLACKBAR_HEIGHT),
            "skinURL= " + HOOD_SKIN_URL + "&neighborhood=" + hotspotData);
    }

    /**
     * Creates a solo game definition, as an object definition string.
     */
    public static String createSoloGameDefinition (String media)
    {
        String definition = CShell.frame.checkFlashVersion(800, 600);
        return definition != null ? definition :
            WidgetUtil.createFlashObjectDefinition("game", media, 800, 600, null);
    }

    /**
     * Toggles the height 100% state of the client.
     */
    public static void toggleClientHeight ()
    {
        if (_clientFullHeight = !_clientFullHeight) {
            setClientHeightNative(findClient(), "100%");
        } else {
            setClientHeightNative(findClient(), Frame.CLIENT_HEIGHT+"px");
        }
    }

    /**
     * Checks if the flash client can be found on this page.
     */
    public static boolean clientExists ()
    {
        return findClient() != null;
    }

    /**
     * Checks to see if the flash client exists and is connected to a server.
     */
    public static boolean clientConnected ()
    {
        return clientConnectedNative(findClient());
    }

    /**
     * Get the current sceneId of the flash client, or 0.
     */
    public static int getSceneId ()
    {
        return getSceneIdNative(findClient());
    }

    /**
     * Checks with the actionscript client to find out if our current scene is in fact a room.
     */
    public static boolean inRoom ()
    {
        return inRoomNative(findClient());
    }

    /**
     * Tells the actionscript client that we'd like to use this item in the current room.  This can
     * be used to add furni, or set the background audio or decor.
     */
    public static void useItem (byte itemType, int itemId)
    {
        useItemNative(findClient(), itemType, itemId);
    }

    /**
     * Tells the actionscript client to remove the given item from use.
     */
    public static void clearItem (byte itemType, int itemId)
    {
        clearItemNative(findClient(), itemType, itemId);
    }

    /**
     * Tells the actionscript client that we'd like to use this avatar.  If 0 is passed in for the
     * avatarId, the current avatar is simply cleared away, leaving you tofulicious.
     */
    public static void useAvatar (int avatarId)
    {
        useAvatarNative(findClient(), avatarId);
    }

    /**
     * Called to start the whirled tour.
     */
    public static void startTour ()
    {
        startTourNative(findClient());
    }

    /**
     * Returns the element that represents the Flash client.
     */
    public static native Element findClient () /*-{
        var client = $wnd.document.getElementById("asclient");
        try {
            if (client == null) {
                client = $wnd.parent.document.getElementById("asclient");
            }
        } catch (e) {
            // we may be running in an iframe on Facebook in which case touching parent will throw
            // an exception, so we just catch that here and go on about our business
        }
        return client;
    }-*/;

    /**
     * Checks if we have a specilized flash object to show, and if so, adds it to the container
     * and returns false, otherwise returns true.
     */
    protected static boolean shouldShowFlash (Panel container, int width, int height)
    {
        String definition = CShell.frame.checkFlashVersion(width, height);
        if (definition != null) {
            WidgetUtil.embedFlashObject(container, definition);
            return false;
        }
        return true;
    }

    /**
     * Returns the height to use for the world/game client.
     */
    protected static String getClientHeight ()
    {
        return _clientFullHeight ? "100%" : (""+Frame.CLIENT_HEIGHT);
    }

    /**
     * TEMP: Changes the height of the client already embedded in the page.
     */
    protected static native void setClientHeightNative (Element client, String height) /*-{
        if (client != null) {
            client.style.height = height;
        }
    }-*/;

    /**
     * Does the actual <code>getSceneId()</code> call.
     */
    protected static native int getSceneIdNative (Element client) /*-{
        if (client) {
            // exceptions from JavaScript break GWT; don't let that happen
            try { return client.getSceneId(); } catch (e) {}
        }
        return 0;
    }-*/;

    /**
     * Does the actual <code>inRoom()</code> call.
     */
    protected static native boolean inRoomNative (Element client) /*-{
        if (client) {
            // exceptions from JavaScript break GWT; don't let that happen
            try { return client.inRoom(); } catch (e) {}
        }
        return false;
    }-*/;

    /**
     * Does the actual <code>useItem()</code> call.
     */
    protected static native void useItemNative (Element client, byte itemType, int itemId) /*-{
        if (client) {
            // exceptions from JavaScript break GWT; don't let that happen
            try { client.useItem(itemType, itemId); } catch (e) {}
        }
    }-*/;

    /**
     * Does the actual <code>clearItem()</code> call.
     */
    protected static native void clearItemNative (Element client, byte itemType, int itemId) /*-{
        if (client) {
            // exceptions from JavaScript break GWT; don't let that happen
            try { client.clearItem(itemType, itemId); } catch (e) {}
        }
    }-*/;

    /**
     * Does the actual <code>useAvatar()</code> call.
     */
    protected static native void useAvatarNative (Element client, int avatarId) /*-{
        if (client) {
            // exceptions from JavaScript break GWT; don't let that happen
            try { client.useAvatar(avatarId); } catch (e) {}
        }
    }-*/;

    /**
     * Does the actual <code>startTour()</code> call.
     */
    protected static native void startTourNative (Element client) /*-{
        if (client) {
            // exceptions from JavaScript break GWT; don't let that happen
            try { client.startTour(); } catch (e) {}
        }
    }-*/;

    /**
     * Does the actual <code>clientConnected()</code> call.
     */
    protected static native boolean clientConnectedNative (Element client) /*-{
        if (client) {
            // exceptions from JavaScript break GWT; don't let that happen
            try { return client.isConnected(); } catch (e) {}
        }
        return false;
    }-*/;

    /** TEMP: Whether or not the client is in full-height mode. */
    protected static boolean _clientFullHeight = false;

    // TODO: put this in Frame?
    protected static final int BLACKBAR_HEIGHT = 20;

    protected static final String HOOD_SKIN_URL = "/media/static/hood_pastoral.swf";
    protected static final int FEATURED_PLACE_WIDTH = 350;
    protected static final int FEATURED_PLACE_HEIGHT = 200;

    protected static final int MIN_INSTALLER_WIDTH = 310;
    protected static final int MIN_INSTALLER_HEIGHT = 137;
}
