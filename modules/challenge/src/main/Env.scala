package lidraughts.challenge

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.game.Game
import lidraughts.hub.TrouperMap
import lidraughts.socket.Socket.{ SocketVersion, GetVersion }
import lidraughts.user.User

final class Env(
    config: Config,
    system: ActorSystem,
    onStart: Game.ID => Unit,
    gameCache: lidraughts.game.Cached,
    lightUser: lidraughts.common.LightUser.GetterSync,
    isOnline: lidraughts.user.User.ID => Boolean,
    hub: lidraughts.hub.Env,
    db: lidraughts.db.Env,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    getPref: User => Fu[lidraughts.pref.Pref],
    getRelation: (User, User) => Fu[Option[lidraughts.relation.Relation]],
    scheduler: lidraughts.common.Scheduler
) {

  private val settings = new {
    val CollectionChallenge = config getString "collection.challenge"
    val MaxPerUser = config getInt "max_per_user"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val SriTimeout = config duration "sri.timeout"
    val SocketTimeout = config duration "socket.timeout"
    val MaxPlaying = config getInt "max_playing"
  }
  import settings._

  private val socketMap: SocketMap = lidraughts.socket.SocketMap[ChallengeSocket](
    system = system,
    mkTrouper = (challengeId: String) => new ChallengeSocket(
      system = system,
      challengeId = challengeId,
      history = new lidraughts.socket.History(ttl = HistoryMessageTtl),
      getChallenge = repo.byId,
      sriTtl = SriTimeout,
      keepMeAlive = () => socketMap touch challengeId
    ),
    accessTimeout = SocketTimeout,
    monitoringName = "challenge.socketMap",
    broomFrequency = 3677 millis
  )

  def version(challengeId: Challenge.ID): Fu[SocketVersion] =
    socketMap.askIfPresentOrZero[SocketVersion](challengeId)(GetVersion)

  lazy val socketHandler = new SocketHandler(
    hub = hub,
    socketMap = socketMap,
    pingChallenge = api.ping
  )

  lazy val api = new ChallengeApi(
    repo = repo,
    joiner = new Joiner(onStart = onStart),
    jsonView = jsonView,
    gameCache = gameCache,
    maxPlaying = MaxPlaying,
    socketMap = socketMap,
    asyncCache = asyncCache,
    lidraughtsBus = system.lidraughtsBus
  )

  lazy val granter = new ChallengeGranter(
    getPref = getPref,
    getRelation = getRelation
  )

  private lazy val repo = new ChallengeRepo(
    coll = db(CollectionChallenge),
    maxPerUser = MaxPerUser
  )

  lazy val jsonView = new JsonView(lightUser, isOnline)

  scheduler.future(3 seconds, "sweep challenges") {
    api.sweep
  }
}

object Env {

  lazy val current: Env = "challenge" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "challenge",
    system = lidraughts.common.PlayApp.system,
    onStart = lidraughts.round.Env.current.onStart,
    hub = lidraughts.hub.Env.current,
    gameCache = lidraughts.game.Env.current.cached,
    lightUser = lidraughts.user.Env.current.lightUserSync,
    isOnline = lidraughts.user.Env.current.isOnline,
    db = lidraughts.db.Env.current,
    asyncCache = lidraughts.memo.Env.current.asyncCache,
    getPref = lidraughts.pref.Env.current.api.getPref,
    getRelation = lidraughts.relation.Env.current.api.fetchRelation,
    scheduler = lidraughts.common.PlayApp.scheduler
  )
}
