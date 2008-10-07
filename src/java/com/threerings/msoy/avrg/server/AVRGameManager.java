//
// $Id$

package com.threerings.msoy.avrg.server;

import java.util.Iterator;
import java.util.Map;

import com.google.inject.Inject;

import com.samskivert.jdbc.WriteOnlyUnit;
import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.HashIntMap;
import com.samskivert.util.Interval;
import com.samskivert.util.Invoker;
import com.samskivert.util.StringUtil;
import com.samskivert.util.IntMap.IntEntry;

import com.threerings.parlor.server.PlayManager;
import com.threerings.presents.annotation.EventThread;
import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationCodes;
import com.threerings.presents.dobj.ObjectDeathListener;
import com.threerings.presents.dobj.ObjectDestroyedEvent;
import com.threerings.presents.dobj.RootDObjectManager;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;

import com.threerings.bureau.server.BureauRegistry;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.server.LocationManager;
import com.threerings.crowd.server.PlaceManager;
import com.threerings.crowd.server.PlaceManagerDelegate;

import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.game.data.MsoyGameDefinition;
import com.threerings.msoy.game.data.PlayerObject;
import com.threerings.msoy.game.server.AgentTraceDelegate;
import com.threerings.msoy.game.server.GameWatcherManager;
import com.threerings.msoy.game.server.TrophyDelegate;
import com.threerings.msoy.game.server.WorldServerClient;
import com.threerings.msoy.game.server.GameWatcherManager.Observer;
import com.threerings.msoy.game.server.PlayerLocator;

import com.threerings.msoy.avrg.data.AVRGameAgentObject;
import com.threerings.msoy.avrg.data.AVRGameConfig;
import com.threerings.msoy.avrg.data.AVRGameObject;
import com.threerings.msoy.avrg.data.PlayerLocation;
import com.threerings.msoy.avrg.data.SceneInfo;
import com.threerings.msoy.avrg.server.AVRGameDispatcher;
import com.threerings.msoy.avrg.server.persist.AVRGameRepository;
import com.threerings.msoy.avrg.server.persist.PlayerGameStateRecord;

import com.whirled.bureau.data.BureauTypes;
import com.whirled.game.server.PrizeDispatcher;
import com.whirled.game.server.PrizeProvider;
import com.whirled.game.server.PropertySpaceDispatcher;
import com.whirled.game.server.PropertySpaceHandler;
import com.whirled.game.server.PropertySpaceHelper;
import com.whirled.game.server.WhirledGameManager;
import com.whirled.game.server.WhirledGameMessageDispatcher;
import com.whirled.game.server.WhirledGameMessageHandler;

import static com.threerings.msoy.Log.log;

/**
 * Manages an AVR game on the server.
 */
@EventThread
public class AVRGameManager extends PlaceManager
    implements AVRGameProvider, PlayManager, AVRGameAgentProvider, PrizeProvider
{
    /** The magic player id constant for the server agent used when sending private messages. */
    public static final int SERVER_AGENT = Integer.MIN_VALUE;

    /** Observes our shutdown function call. */
    public interface LifecycleObserver
    {
        /** Informs the observer the AVRG manager is ready for operation. */
        void avrGameReady (AVRGameManager mgr);

        /** Informs the observer that our agent could not start. */
        void avrGameAgentFailedToStart (AVRGameManager mgr);

        /** Informs the observer a shutdown has happened. */
        void avrGameDidShutdown (AVRGameManager mgr);

        /** Informs the observer that our agent has died abnormally. This could happen for example
         * if the bureau process that owns the agent stops responding. */
        void avrGameAgentDestroyed (AVRGameManager mgr);
    }

    public void setLifecycleObserver (LifecycleObserver obs)
    {
        _lifecycleObserver = obs;
    }

    public int getGameId ()
    {
        return _gameId;
    }

    public AVRGameObject getGameObject ()
    {
        return _gameObj;
    }

    public AVRGameAgentObject getGameAgentObject ()
    {
        return _gameAgentObj;
    }

    @Override // from PlaceManager
    public void addDelegate (PlaceManagerDelegate delegate)
    {
        super.addDelegate(delegate);

        if (delegate instanceof QuestDelegate) {
            _questDelegate = (QuestDelegate) delegate;

        } else if (delegate instanceof TrophyDelegate) {
            _trophyDelegate = (TrophyDelegate) delegate;

        } else if (delegate instanceof AgentTraceDelegate) {
            _traceDelegate = (AgentTraceDelegate) delegate;
        }
    }

    // from PlayManager
    public boolean isPlayer (ClientObject client)
    {
        return client != null && (client instanceof PlayerObject) &&
            _gameObj.occupants.contains(client.getOid());
    }

    // from PlayManager
    public boolean isAgent (ClientObject caller)
    {
        return _gameAgentObj != null && _gameAgentObj.clientOid == caller.getOid();
    }

    // from PlayManager
    public BodyObject checkWritePermission (ClientObject caller, int playerId)
    {
        if (isAgent(caller)) {
            return (playerId != 0) ? _locator.lookupPlayer(playerId) : null;
        }
        return (playerId == 0) ? (BodyObject) caller : null;
    }

    /**
     * Called privately by the ThaneAVRGameController when anything in the agent's code domain
     * causes a line of debug or error tracing.
     */
    public void agentTrace (ClientObject caller, String[] traces)
    {
        _traceDelegate.recordAgentTrace(traces);
    }

    @Override
    protected PlaceObject createPlaceObject ()
    {
        return new AVRGameObject();
    }

    @Override
    protected void didStartup ()
    {
        super.didStartup();

        AVRGameConfig cfg = (AVRGameConfig)_config;

        _gameId = cfg.getGameId();

        _gameObj = (AVRGameObject)_plobj;
        _gameObj.setAvrgService(_invmgr.registerDispatcher(new AVRGameDispatcher(this)));
        _gameObj.setPrizeService(_invmgr.registerDispatcher(new PrizeDispatcher(this)));
        _gameObj.setMessageService(_invmgr.registerDispatcher(new WhirledGameMessageDispatcher(
            new WhirledGameMessageHandler(_gameObj) {
                @Override protected ClientObject getAudienceMember (int id)
                    throws InvocationException {
                    ClientObject target = null;
                    if (id == SERVER_AGENT) {
                        if (_gameAgentObj != null && _gameAgentObj.clientOid != 0) {
                            target = (ClientObject)_omgr.getObject(_gameAgentObj.clientOid);
                        }
                    } else {
                        target = _locator.lookupPlayer(id);
                    }
                    if (target == null) {
                        throw new InvocationException("m.player_not_around");
                    }
                    return target;
                }

                @Override protected void validateSender (ClientObject caller)
                    throws InvocationException {
                    validateUser(caller);
                }

                @Override protected boolean isAgent (ClientObject caller) {
                    return AVRGameManager.this.isAgent(caller);
                }

                @Override protected int resolvePlayerId (ClientObject caller) {
                    return ((PlayerObject)caller).getMemberId();
                }
            })));
        _gameObj.setPropertiesService(_invmgr.registerDispatcher(new PropertySpaceDispatcher(
            new PropertySpaceHandler(_gameObj) {
                @Override protected void validateUser (ClientObject caller)
                    throws InvocationException {
                    if (!isAgent(caller)) {
                        throw new InvocationException(InvocationCodes.ACCESS_DENIED);
                    }
                }
            })));

        _sceneCheck = new Interval(_omgr) {
            public void expired () {
                checkScenes();
            }
        };
        _sceneCheck.schedule(SCENE_CHECK_PERIOD, true);
    }

    public void startAgent ()
    {
        if (_gameAgentObj != null) {
            log.warning("AVRG already has started agent", "agent", _gameAgentObj);
            return;
        }

        AVRGameConfig cfg = (AVRGameConfig)_config;
        MsoyGameDefinition def = (MsoyGameDefinition) cfg.getGameDefinition();

        _gameAgentObj = createGameAgentObject(_gameId, def);
        if (_gameAgentObj == null) {
            // if there is no agent, force a call to agentReady
            _lifecycleObserver.avrGameReady(this);
            return;
        }

        // else ask the bureau to start it and wait for its initialization
        _gameAgentObj.gameOid = _gameObj.getOid();
        _breg.startAgent(_gameAgentObj);

        // inform the observer if the agent dies and we didn't kill it
        _gameAgentObj.addListener(new ObjectDeathListener() {
            public void objectDestroyed (ObjectDestroyedEvent event) {
                if (_gameAgentObj != null) {
                    log.info("Game agent destroyed", "gameId", getGameId(), "agent", _gameAgentObj);
                    if (_lifecycleObserver != null) {
                        _lifecycleObserver.avrGameAgentDestroyed(AVRGameManager.this);
                    }
                }
            }
        });
    }

    // from interface PrizeProvider
    public void awardTrophy (ClientObject caller, String ident, int playerId,
                             InvocationService.InvocationListener listener)
        throws InvocationException
    {
        _trophyDelegate.awardTrophy(caller, ident, playerId, listener);
    }

    // from interface PrizeProvider
    public void awardPrize (ClientObject caller, String ident, int playerId,
                            InvocationService.InvocationListener listener)
        throws InvocationException
    {
        _trophyDelegate.awardPrize(caller, ident, playerId, listener);
    }

    // from AVRGameProvider
    public void completeTask (ClientObject caller, final int playerId, final String questId,
                              final float payoutLevel, InvocationService.ConfirmListener listener)
        throws InvocationException
    {
        PlayerObject player;
        if (playerId == 0) {
            player = (PlayerObject) caller;

        } else if (isAgent(caller)) {
            player = _locator.lookupPlayer(playerId);
            if (player == null) {
                log.warning("Agent call to completeTask for unknown player", "caller", caller,
                            "playerId", playerId);
                return;
            }

        } else {
            log.warning("Non-agent calling completeTask", "caller", caller, "playerId", playerId);
            return;
        }

        _questDelegate.completeTask(player, questId, payoutLevel, listener);
    }

    // from AVRGameAgentProvider
    public void roomSubscriptionComplete (ClientObject caller, int sceneId)
    {
        if (caller.getOid() != _gameAgentObj.clientOid) {
            log.warning("Unknown client completing room subscription?", "caller", caller);
            return;
        }

        roomSubscriptionComplete(sceneId);
    }

    /**
     * Called privately by the ThaneAVRGameController when an agent's code is all set to go
     * and the AVRG can startup.
     */
    public void agentReady (ClientObject caller)
    {
        log.info(
            "AVRG Agent ready", "clientOid", caller.getOid(), "agentOid", _gameAgentObj.getOid());
        _lifecycleObserver.avrGameReady(this);
    }

    /**
     * Called privately by the ThaneAVRGameController when an agent's code could not be loaded.
     */
    public void agentFailed (ClientObject caller)
    {
        log.info(
            "AVRG Agent failed", "clientOid", caller.getOid(), "agentOid", _gameAgentObj.getOid());
        _lifecycleObserver.avrGameAgentFailedToStart(this);
    }

    // From AVRGameAgentProvider
    public void leaveGame (ClientObject caller, int playerId)
    {
        if (!isAgent(caller)) {
            log.warning("Non agent calling leaveGame", "caller", caller);
            return;
        }
        PlayerObject player = _locator.lookupPlayer(playerId);
        if (player == null) {
            log.info("Agent requested non-player to leave", "caller", caller,
                "playerId", playerId);
            return;
        }
        _locmgr.leavePlace(player);

        // Make sure we notify the world server too, since we are officially deactivating this
        // game as opposed to just leaving it tempoararily.
        _worldClient.leaveAVRGame(playerId);
    }

    /**
     * This method is called when the agent completes the subscription of a scene's RoomObject.
     */
    public void roomSubscriptionComplete (int sceneId)
    {
        Scene scene = _scenes.get(sceneId);

        if (scene == null) {
            log.warning("Subscription completed to removed scene", "sceneId", sceneId);
            return;
        }

        scene.subscribed = true;

        for (int memberId : scene.players) {
            postPlayerMove(memberId, scene.sceneId);
        }
    }

    /**
     * Post the given member's movement to the game object for all to see.
     */
    protected void postPlayerMove (int memberId, int sceneId)
    {
        PlayerLocation loc = new PlayerLocation(memberId, sceneId);
        if (_gameObj.playerLocs.contains(loc)) {
            _gameObj.updatePlayerLocs(loc);

        } else {
            _gameObj.addToPlayerLocs(loc);
        }
    }

    @Override
    protected void didShutdown ()
    {
        if (_gameAgentObj != null) {
            _invmgr.clearDispatcher(_gameAgentObj.agentService);
            _breg.destroyAgent(_gameAgentObj);
            _gameAgentObj = null;
        }

        if (_lifecycleObserver != null) {
            _lifecycleObserver.avrGameDidShutdown(this);
        }

        _invmgr.clearDispatcher(_gameObj.avrgService);
        _invmgr.clearDispatcher(_gameObj.messageService);
        _invmgr.clearDispatcher(_gameObj.propertiesService);

        _sceneCheck.cancel();

        super.didShutdown();
    }

    @Override
    protected void bodyEntered (int bodyOid)
    {
        super.bodyEntered(bodyOid);

        PlayerObject player = (PlayerObject) _omgr.getObject(bodyOid);
        _watchmgr.addWatch(player.getMemberId(), _observer);

        PropertySpaceHandler handler = new PropertySpaceHandler(player) {
            @Override protected void validateUser (ClientObject caller)
                throws InvocationException {
                AVRGameManager.this.validateUser(caller);
            }
        };

        player.setPropertyService(_invmgr.registerDispatcher(new PropertySpaceDispatcher(handler)));
    }

    @Override
    protected void bodyLeft (int bodyOid)
    {
        super.bodyLeft(bodyOid);

        PlayerObject player = (PlayerObject) _omgr.getObject(bodyOid);

        _invmgr.clearDispatcher(player.propertyService);

        int memberId = player.getMemberId();
        // stop watching this player's movements
        _watchmgr.clearWatch(memberId);

        // clear out from our internal record
        Scene scene = _players.remove(memberId);
        if (scene == null) {
            log.warning("Leaving body has no scene", "memberId", memberId);
        } else {
            scene.removePlayer(memberId);

            // remove from player locations
            if (_gameObj.playerLocs.containsKey(memberId)) {
                _gameObj.removeFromPlayerLocs(memberId);

            } else if (scene.subscribed) {
                log.warning("Player leaving subscribed scene not in playerLocs?",
                    "scene", scene, "memberId", memberId);
            }
        }

        flushPlayerGameState(player);
    }

    @Override
    protected long idleUnloadPeriod ()
    {
        return IDLE_UNLOAD_PERIOD;
    }

    protected void playerEnteredScene (int memberId, int sceneId, String hostname, int port)
    {
        log.debug(
            "Player entered scene [memberId=" + memberId + ", sceneId=" + sceneId +
            ", hostname=" + hostname + ", port=" + port + "]");

        if (sceneId == 0) {
            log.info("Ignoring entry of scene 0", "memberId", memberId);
            return;
        }
        
        SceneInfo info = new SceneInfo(sceneId, hostname, port);
        SceneInfo existing = _gameAgentObj.scenes.get(info.getKey());
        if (existing != null) {
            if (!info.equals(existing)) {
                // this should not happen in normal operation, but is feasible if a server goes
                // down abruptly
                log.warning("Updating stale SceneInfo", "old", existing, "new", info);
                _gameAgentObj.updateScenes(info);
            }

        } else {
            _gameAgentObj.addToScenes(info);
        }

        // Update our internal records
        Scene oldScene = _players.get(memberId);
        if (oldScene != null && sceneId == oldScene.sceneId) {
            log.info("Player entered scene twice", "memberId", memberId, "scene", oldScene);

        } else {
            Scene scene = _scenes.get(sceneId);
            if (scene == null) {
                _scenes.put(sceneId, scene = new Scene(sceneId));
            }
            _players.put(memberId, scene);
            scene.addPlayer(memberId);
            if (oldScene != null) {
                oldScene.removePlayer(memberId);
            }

            // Expose the transfer to dobj (or else wait until agent calls roomSubscriptionComplete)
            if (scene.subscribed || _gameAgentObj==null) {
                postPlayerMove(memberId, sceneId);
            }
        }
    }

    protected void flushPlayerGameState (PlayerObject player)
    {
        final int memberId = player.getMemberId();
        final Map<String, byte[]> state = PropertySpaceHelper.encodeDirtyStateForStore(player);
        if (state.size() != 0 && !MemberName.isGuest(memberId)) {
            _invoker.postUnit(new WriteOnlyUnit("flushPlayerAVRGState") {
                @Override public void invokePersist ()
                    throws Exception {
                    for (Map.Entry<String, byte[]> entry : state.entrySet()) {
                        _repo.storePlayerState(new PlayerGameStateRecord(
                            _gameId, memberId, entry.getKey(), entry.getValue()));
                    }
                }
            });
        }
    }

    /**
     * Reports the id/name of this game for use in log messages.
     */
    @Override
    public String where ()
    {
        return String.valueOf(_gameId);
    }

    protected AVRGameAgentObject createGameAgentObject (int gameId, MsoyGameDefinition def)
    {
        String code = def.getServerMediaPath(gameId);
        if (StringUtil.isBlank(code)) {
            return null;
        }

        AVRGameAgentObject agent = new AVRGameAgentObject();
        agent.bureauId = BureauTypes.GAME_BUREAU_ID_PREFIX + gameId;
        agent.bureauType = BureauTypes.THANE_BUREAU_TYPE;
        agent.gameId = gameId;
        agent.code = code;
        agent.agentService = _invmgr.registerDispatcher(new AVRGameAgentDispatcher(this));
        if (StringUtil.isBlank(def.server)) {
            agent.className = WhirledGameManager.DEFAULT_SERVER_CLASS;
        } else {
            agent.className = def.server;
        }
        return agent;
    }

    /**
     * Eliminate any scenes that have been empty for a while. This is to prevent a large buildup
     * for games that visit a lot of rooms.
     */
    protected void checkScenes ()
    {
        long now = System.currentTimeMillis();
        Iterator<IntEntry<Scene>> iter = _scenes.intEntrySet().iterator();
        while (iter.hasNext()) {
            Scene scene = iter.next().getValue();
            if (scene.shouldFlush(now)) {
                if (_gameAgentObj.scenes.containsKey(scene.sceneId)) {
                    _gameAgentObj.removeFromScenes(scene.sceneId);

                } else {
                    log.warning("Flushing scene not found in agent", "scene", scene);
                }
                iter.remove();
            }
        }
    }

    /**
     * Throws an InvocationException if the caller is not allowed.
     */
    protected void validateUser (ClientObject caller)
        throws InvocationException
    {
        if (!isPlayer(caller) && !isAgent(caller)) {
            throw new InvocationException(InvocationCodes.ACCESS_DENIED);
        }
    }

    /**
     * Data for tracking what players are in a scene, so that we know when to flush it.
     */
    protected static class Scene
    {
        public int sceneId;
        public long modTime;
        public boolean subscribed;
        public ArrayIntSet players = new ArrayIntSet();

        public Scene (int sceneId)
        {
            this.sceneId = sceneId;
            touch();
        }

        /** Sets the last modification time to the current time. */
        public void touch ()
        {
            modTime = System.currentTimeMillis();
        }

        /** Test if the scene has been empty for a while. */
        public boolean shouldFlush (long now)
        {
            if (players.size() > 0) {
                return false;
            }

            return (now - modTime > SCENE_IDLE_UNLOAD_PERIOD);
        }

        /** Add a player to the scene, automatically calling {@link #touch} as well. */
        public void addPlayer (int id)
        {
            if (!players.add(id)) {
                log.warning("Player added to scene twice", "memberId", id, "sceneId", sceneId);
            }
            touch();
        }

        /** Remove a player from the scene, automatically calling {@link #touch} as well. */
        public void removePlayer (int id)
        {
            if (!players.remove(id)) {
                log.warning("Player not found to remove", "memberId", id, "sceneId", sceneId);
            }
            touch();
        }

        public String toString ()
        {
            StringBuilder buf = new StringBuilder();
            buf.append("AVRGameManager.Scene");
            StringUtil.fieldsToString(buf, this);
            return buf.toString();
        }
    }

    protected Observer _observer = new Observer() {
        public void memberMoved (int memberId, int sceneId, String hostname, int port) {
            playerEnteredScene(memberId, sceneId, hostname, port);
        }
    };

    /** The gameId of this particular AVRG. */
    protected int _gameId;

    /** The distributed object that both clients and the agent sees. */
    protected AVRGameObject _gameObj;

    /** The distributed object that only our agent sees. */
    protected AVRGameAgentObject _gameAgentObj;

    /** The map of scenes by scene id, one to one. */
    protected HashIntMap<Scene> _scenes = new HashIntMap<Scene>();

    /** The map of player ids to scenes, many to one. */
    protected HashIntMap<Scene> _players = new HashIntMap<Scene>();

    /** Interval to run our scene checker. */
    protected Interval _sceneCheck;

    /** Observer of our shutdown. */
    protected LifecycleObserver _lifecycleObserver;

    /** The delegate that handles quest completion and coin payouts. */
    protected QuestDelegate _questDelegate;

    /** A delegate that takes care of awarding trophies and prizes.. */
    protected TrophyDelegate _trophyDelegate;

    /** A delegate that handles agent traces.. */
    protected AgentTraceDelegate _traceDelegate;

    // our dependencies
    @Inject protected @MainInvoker Invoker _invoker;
    @Inject protected InvocationManager _invmgr;
    @Inject protected GameWatcherManager _watchmgr;
    @Inject protected AVRGameRepository _repo;
    @Inject protected BureauRegistry _breg;
    @Inject protected RootDObjectManager _omgr;
    @Inject protected PlayerLocator _locator;
    @Inject protected LocationManager _locmgr;
    @Inject protected WorldServerClient _worldClient;

    /** idle time before shutting down the manager. */
    protected static final long IDLE_UNLOAD_PERIOD =
        DeploymentConfig.devDeployment ? 30*1000L : 5*60*1000L; // in ms

    /** Minimum time a scene must remain idle before unloading. */
    protected static final long SCENE_IDLE_UNLOAD_PERIOD =
        DeploymentConfig.devDeployment ? 10*1000L : 60*1000L; // in ms

    /** Time between checks to flush idle scenes. */
    protected static final long SCENE_CHECK_PERIOD =
        DeploymentConfig.devDeployment ? 5*1000L : 60*1000L; // in ms
}
