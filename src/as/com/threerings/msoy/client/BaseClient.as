//
// $Id$

package com.threerings.msoy.client {

import flash.display.DisplayObject;
import flash.display.Stage;

import flash.external.ExternalInterface;
import flash.system.Security;

import flash.media.SoundMixer;
import flash.media.SoundTransform;

import mx.resources.ResourceBundle;

import com.adobe.crypto.MD5;

import com.threerings.util.Log;
import com.threerings.util.Name;
import com.threerings.util.ResultAdapter;
import com.threerings.util.StringUtil;
import com.threerings.util.ValueEvent;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.ClientAdapter;
import com.threerings.presents.client.ClientEvent;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.DSet;

import com.threerings.presents.dobj.DObjectManager;

import com.threerings.presents.net.BootstrapData;

import com.threerings.presents.data.TimeBaseMarshaller;
import com.threerings.crowd.data.BodyMarshaller;
import com.threerings.crowd.data.LocationMarshaller;
import com.threerings.crowd.chat.data.ChatMarshaller;

import com.threerings.msoy.chat.client.MsoyChatDirector;
import com.threerings.msoy.chat.data.ChatChannel;

import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.data.all.GroupName;
import com.threerings.msoy.data.all.ChannelName;
import com.threerings.msoy.data.all.SceneBookmarkEntry;
import com.threerings.msoy.data.MemberInfo;
import com.threerings.msoy.data.MemberMarshaller;
import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyAuthResponseData;
import com.threerings.msoy.data.MsoyBootstrapData;
import com.threerings.msoy.data.MsoyCredentials;

/**
 * A client shared by both our virtual world and header incarnations.
 */
public /*abstract*/ class BaseClient extends Client
{
    public static const log :Log = Log.getLog(BaseClient);

    public function dispatchEventToGWT (eventName :String, eventArgs :Array) :void
    {
        try {
            if (ExternalInterface.available && !_embedded) {
                ExternalInterface.call("triggerFlashEvent", eventName, eventArgs);
            }
        } catch (err :Error) {
            Log.getLog(this).warning("triggerFlashEvent failed: " + err);
        }
    }

    public function BaseClient (stage :Stage)
    {
        super(createStartupCreds(stage), stage);
        setVersion(DeploymentConfig.version);

        var params :Object = stage.loaderInfo.parameters;
        _featuredPlaceView = params["featuredPlace"] != null;
        if (_featuredPlaceView) {
            // mute all sound in featured place view.
            var mute :SoundTransform = new SoundTransform();
            mute.volume = 0;
            SoundMixer.soundTransform = mute;
        }

        _ctx = createContext();
        LoggingTargets.configureLogging(_ctx);
        
        // wire up our JavaScript bridge functions
        try {
            if (ExternalInterface.available) {
                configureExternalFunctions();
            }
        } catch (err :Error) {
            _embedded = true;
            // nada: ExternalInterface isn't there. Oh well!
            log.info("Unable to configure external functions.");
        }

        // allow connecting the media server if it differs from the game server
        if (DeploymentConfig.mediaURL.indexOf(DeploymentConfig.serverHost) == -1) {
            Security.loadPolicyFile(DeploymentConfig.mediaURL + "crossdomain.xml");
        }

        // prior to logging on to a server, set up our security policy for that server
        addClientObserver(new ClientAdapter(clientWillLogon));

        // configure our server and port info and logon
        setServer(getServerHost(stage), getServerPorts(stage));
        _httpPort = getHttpServerPort(stage);
    }

    public function fuckingCompiler () :void
    {
        var i :int = TimeBaseMarshaller.GET_TIME_OID;
        i = LocationMarshaller.LEAVE_PLACE;
        i = BodyMarshaller.SET_IDLE;
        i = ChatMarshaller.AWAY;

        var c :Class;
        c = MsoyBootstrapData;
        c = MemberObject;
        c = MemberInfo;
        c = MsoyAuthResponseData;
        c = MemberMarshaller;
        c = SceneBookmarkEntry;

        [ResourceBundle("global")]
        var rb :ResourceBundle;
    }

    // from Client
    override public function gotBootstrap (data :BootstrapData, omgr :DObjectManager) :void
    {
        super.gotBootstrap(data, omgr);

        // save any machineIdent or sessionToken from the server.
        var rdata :MsoyAuthResponseData = (getAuthResponseData() as MsoyAuthResponseData);
        if (rdata.ident != null) {
            Prefs.setMachineIdent(rdata.ident);
        }
        if (rdata.sessionToken != null) {
            Prefs.setSessionToken(rdata.sessionToken);
            // fill our session token into our credentials so that we can log in more efficiently
            // on a reconnect and so that we can log into game servers
            (getCredentials() as MsoyCredentials).sessionToken = rdata.sessionToken;
        }

        if (rdata.sessionToken != null) {
            try {
                if (ExternalInterface.available && !_embedded) {
                    ExternalInterface.call("flashDidLogon", "Foo", 1, rdata.sessionToken);
                }
            } catch (err :Error) {
                log.warning("Unable to inform javascript about login: " + err);
            }
        }

        log.info("Client logged on [built=" + DeploymentConfig.buildTime +
                 ", mediaURL=" + DeploymentConfig.mediaURL +
                 ", staticMediaURL=" + DeploymentConfig.staticMediaURL + "].");
    }

    // from Client
    override public function gotClientObject (clobj :ClientObject) :void
    {
        super.gotClientObject(clobj);

        // set up our logging targets
        LoggingTargets.configureLogging(_ctx);

        if (!_featuredPlaceView) {
            // listen for flow and gold updates
            _user = (clobj as MemberObject);
            var updater :StatusUpdater = new StatusUpdater(this);
            _user.addListener(updater);

            // configure our levels to start
            updater.newLevel(_user.level);
            // updater.newGold(_user.gold);
            updater.newFlow(_user.flow);
            updater.newMail(_user.newMailCount);
        }
    }

    /**
     * Find out whether this client is embedded in a non-whirled page.
     */
    public function isEmbedded () :Boolean
    {
        return _embedded;
    }

    /**
     * Find out whether this client is being used as a featured place view.
     */
    public function isFeaturedPlaceView () :Boolean
    {
        return _featuredPlaceView;
    }

    /**
     * Returns the port on which we can connect to the HTTP server.
     */
    public function getHttpPort () :int
    {
        return _httpPort;
    }
     
    /**
     * Called just before we logon to a server.
     */
    protected function clientWillLogon (event :ClientEvent) :void
    {
        log.info("Loading policy for host " + getHostname()  + ".");
        Security.loadPolicyFile("http://" + getHostname() + "/crossdomain.xml");
    }

    /**
     * Configure any external functions that we wish to expose to JavaScript.
     */
    protected function configureExternalFunctions () :void
    {
        ExternalInterface.addCallback("onUnload", externalOnUnload);
        ExternalInterface.addCallback("openChannel", externalOpenChannel);
    }

    /**
     * Exposed to JavaScript so that it may notify us when we're leaving the page.
     */
    protected function externalOnUnload () :void
    {
        log.info("Client unloaded. Logging off.");
        logoff(false);
    }

    /**
     * Exposed to JavaScript so that it may order us to open chat channels.
     */
    protected function externalOpenChannel (type :int, name :String, id :int) :void
    {
        var nameObj :Name;
        if (type == ChatChannel.MEMBER_CHANNEL) {
            nameObj = new MemberName(name, id);
        } else if (type == ChatChannel.GROUP_CHANNEL) {
            nameObj = new GroupName(name, id);
        } else if (type == ChatChannel.PRIVATE_CHANNEL) {
            nameObj = new ChannelName(name, id);
        } else {
            throw new Error("Unknown channel type: " + type);
        }
        (_ctx as WorldContext).getMsoyChatDirector().openChannel(nameObj);
    }

    /**
     * Creates the context we'll use with this client.
     */
    protected function createContext () :BaseContext
    {
        return new BaseContext(this);
    }

    /**
     * Create the credentials that will be used to log us on
     */
    protected static function createStartupCreds (stage :Stage, token :String = null)
        :MsoyCredentials
    {
        var params :Object = stage.loaderInfo.parameters;
        var creds :MsoyCredentials;
        if ((params["pass"] != null) && (params["user"] != null)) {
            creds = new MsoyCredentials(new Name(String(params["user"])),
                                        MD5.hash(String(params["pass"])));
        } else {
            creds = new MsoyCredentials(null, null);
        }
        creds.ident = Prefs.getMachineIdent();
        if (null == params["guest"]) {
            creds.sessionToken = (token == null) ? params["token"] : token;
        }
        creds.featuredPlaceView = null != params["featuredPlace"];
        return creds;
    }

    /**
     * Returns the hostname of the game server to which we should connect, first checking the movie
     * parameters, then falling back to the default in DeploymentConfig.
     */
    protected static function getServerHost (stage :Stage) :String
    {
        var params :Object = stage.loaderInfo.parameters;
        return (params["host"] != null) ? String(params["host"]) : DeploymentConfig.serverHost;
    }

    /**
     * Returns the ports on which we should connect to the game server, first checking the movie
     * parameters, then falling back to the default in DeploymentConfig.
     */
    protected static function getServerPorts (stage :Stage) :Array
    {
        var params :Object = stage.loaderInfo.parameters;
        return (params["port"] != null) ?
            [ int(parseInt(params["port"])) ] : DeploymentConfig.serverPorts;
    }

    /**
     * Returns the port on which we can connect to the HTTP server, first checking the movie
     * parameters, then falling back to the default in DeploymentConfig.
     */
    protected static function getHttpServerPort (stage :Stage) :int
    {
        var params :Object = stage.loaderInfo.parameters;
        return (params["httpPort"] != null) ?
            int(parseInt(params["httpPort"])) : DeploymentConfig.httpPort;
    }

    protected var _ctx :BaseContext;
    protected var _user :MemberObject;
    protected var _embedded :Boolean = false;
    protected var _featuredPlaceView :Boolean = false;

    /** The port on which we connect to the HTTP server. */
    protected var _httpPort :int;
}
}

import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.SetListener;

import com.threerings.msoy.client.BaseClient;
import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.all.FriendEntry;
import com.threerings.msoy.data.all.SceneBookmarkEntry;

class StatusUpdater implements AttributeChangeListener, SetListener
{
    public function StatusUpdater (client :BaseClient) {
        _client = client;
    }

    public function attributeChanged (event :AttributeChangedEvent) :void {
        if (event.getName() == MemberObject.LEVEL) {
            newLevel(event.getValue() as int, event.getOldValue() as int);
        /*} else if (event.getName() == MemberObject.GOLD) {
            newGold(event.getValue() as int, event.getOldValue() as int); */
        } else if (event.getName() == MemberObject.FLOW) {
            newFlow(event.getValue() as int, event.getOldValue() as int);
        } else if (event.getName() == MemberObject.NEW_MAIL_COUNT) {
            newMail(event.getValue() as int, event.getOldValue() as int);
        }
    }

    public function entryAdded (event :EntryAddedEvent) :void {
        if (event.getName() == MemberObject.FRIENDS) {
            var entry :FriendEntry = (event.getEntry() as FriendEntry);
            _client.dispatchEventToGWT(
                FRIEND_EVENT, [FRIEND_ADDED, entry.name.toString(), entry.name.getMemberId()]);
        } else if (event.getName() == MemberObject.OWNED_SCENES) {
            var scene :SceneBookmarkEntry = (event.getEntry() as SceneBookmarkEntry);
            _client.dispatchEventToGWT(
                SCENEBOOKMARK_EVENT, [SCENEBOOKMARK_ADDED, scene.sceneName, scene.sceneId]);
        }
    }

    public function entryUpdated (event :EntryUpdatedEvent) :void {
        // nada
    }

    public function entryRemoved (event :EntryRemovedEvent) :void {
        if (event.getName() == MemberObject.FRIENDS) {
            var memberId :int = int(event.getKey());
            _client.dispatchEventToGWT(FRIEND_EVENT, [FRIEND_REMOVED, "", memberId]);
        } else if (event.getName() == MemberObject.OWNED_SCENES) {
            var sceneId :int = int(event.getKey());
            _client.dispatchEventToGWT(
                SCENEBOOKMARK_EVENT, [SCENEBOOKMARK_REMOVED, "", sceneId]);
        }
    }

    public function newLevel (level :int, oldLevel :int = 0) :void {
        sendNotification([STATUS_CHANGE_LEVEL, level, oldLevel]);
    }

    public function newFlow (flow :int, oldFlow :int = 0) :void {
        sendNotification([STATUS_CHANGE_FLOW, flow, oldFlow]);
    }

    public function newGold (gold :int, oldGold :int = 0) :void {
        sendNotification([STATUS_CHANGE_GOLD, gold, oldGold]);
    }

    public function newMail (mail :int, oldMail :int = -1) :void {
        sendNotification([STATUS_CHANGE_MAIL, mail, oldMail]);
    }

    protected function sendNotification (args :Array) :void {
        _client.dispatchEventToGWT(STATUS_CHANGE_EVENT, args);
    }

    /** Event dispatched to GWT when we've leveled up */
    protected static const STATUS_CHANGE_EVENT :String = "statusChange";
    protected static const STATUS_CHANGE_LEVEL :int = 1;
    protected static const STATUS_CHANGE_FLOW :int = 2;
    protected static const STATUS_CHANGE_GOLD :int = 3;
    protected static const STATUS_CHANGE_MAIL :int = 4;

    protected static const FRIEND_EVENT :String = "friend";
    protected static const FRIEND_ADDED :int = 1;
    protected static const FRIEND_REMOVED :int = 2;

    protected static const SCENEBOOKMARK_EVENT :String = "sceneBookmark";
    protected static const SCENEBOOKMARK_ADDED :int = 1;
    protected static const SCENEBOOKMARK_REMOVED :int = 2;

    protected var _client :BaseClient;
}
