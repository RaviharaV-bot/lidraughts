package lidraughts.challenge

import akka.actor._
import org.joda.time.DateTime
import scala.concurrent.duration._

import lidraughts.game.{ Game, Pov }
import lidraughts.hub.actorApi.map.Tell
import lidraughts.hub.actorApi.socket.SendTo
import lidraughts.user.{ User, UserRepo }

final class ChallengeApi(
    repo: ChallengeRepo,
    joiner: Joiner,
    jsonView: JsonView,
    gameCache: lidraughts.game.Cached,
    maxPlaying: Int,
    socketMap: SocketMap,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lidraughtsBus: lidraughts.common.Bus
) {

  import Challenge._

  def allFor(userId: User.ID): Fu[AllChallenges] =
    createdByDestId(userId) zip createdByChallengerId(userId) map (AllChallenges.apply _).tupled

  def allForExternalTournament(tourId: String): Fu[List[Challenge]] =
    repo externalByTournamentId tourId

  // returns boolean success
  def create(c: Challenge): Fu[Boolean] =
    if (c.isExternal) (repo insert c) >>- {
      lidraughtsBus.publish(Event.Create(c), 'challenge)
    } inject true
    else isLimitedByMaxPlaying(c) flatMap {
      case true => fuFalse
      case false => {
        repo like c flatMap { _ ?? repo.cancel }
      } >> (repo insert c) >>- {
        uncacheAndNotify(c)
        lidraughtsBus.publish(Event.Create(c), 'challenge)
      } inject true
    }

  def byId = repo byId _

  def activeByIdFor(id: Challenge.ID, dest: User) = repo.byIdFor(id, dest).map(_.filter(_.active))

  def onlineByIdFor(id: Challenge.ID, dest: User) = repo.byIdFor(id, dest).map(_.filter(_.online))

  val countInFor = asyncCache.clearable(
    name = "challenge.countInFor",
    f = repo.countCreatedByDestId,
    expireAfter = _.ExpireAfterAccess(20 minutes)
  )

  def createdByChallengerId = repo createdByChallengerId _

  def createdByDestId = repo createdByDestId _

  def cancel(c: Challenge) = (repo cancel c) >>- uncacheAndNotify(c)

  private def offline(c: Challenge) = (repo offline c) >>- uncacheAndNotify(c)

  private[challenge] def ping(id: Challenge.ID): Funit = repo statusById id flatMap {
    case Some(Status.Created) => repo setSeen id
    case Some(Status.Offline) => (repo setSeenAgain id) >> byId(id).map { _ foreach uncacheAndNotify }
    case _ => fuccess(socketReload(id))
  }

  def decline(c: Challenge) = (repo decline c) >>- uncacheAndNotify(c)

  def accept(c: Challenge, user: Option[User]): Fu[Option[Pov]] =
    if (c.isExternal) acceptExternal(c, user)
    else joiner(c, user).flatMap {
      case None => fuccess(None)
      case Some(pov) => (repo accept c) >>- {
        uncacheAndNotify(c)
        lidraughtsBus.publish(Event.Accept(c, user.map(_.id)), 'challenge)
      } inject pov.some
    }

  private def acceptExternal(challenge: Challenge, user: Option[User]): Fu[Option[Pov]] =
    user match {
      case Some(u) if challenge.external.flatMap(_.startsAt).fold(true)(_.isBeforeNow) =>
        val c = challenge.acceptExternal(u)
        doAcceptExternal(c)
      case _ => fuccess(None)
    }

  private def doAcceptExternal(c: Challenge) =
    (c.external ?? { _.bothAccepted }) ?? {
      c.destUserId.??(UserRepo.byId) flatMap { destUser => joiner(c, destUser) }
    } flatMap {
      case None =>
        (repo update c) >>- {
          socketReload(c.id)
        } inject None
      case Some(pov) =>
        val c2 = c.copy(status = Status.Accepted, expiresAt = DateTime.now.plusHours(3))
        (repo update c2) >>- {
          socketReload(c2.id)
        } inject pov.some
    }

  private def autoStart(c: Challenge) =
    c.external.fold(funit) { external =>
      doAcceptExternal(c.withExternal(
        external.copy(
          challengerAccepted = true,
          destUserAccepted = true
        )
      )).void
    }

  def sendRematchOf(game: Game, user: User): Fu[Boolean] =
    ChallengeMaker.makeRematchOf(game, user) flatMap { _ ?? create }

  def setDestUser(c: Challenge, u: User): Funit = {
    val challenge = c setDestUser u
    repo.update(challenge) >>- {
      uncacheAndNotify(challenge)
      lidraughtsBus.publish(Event.Create(challenge), 'challenge)
    }
  }

  def removeByUserId(userId: User.ID) = repo allWithUserId userId flatMap { cs =>
    lidraughts.common.Future.applySequentially(cs)(remove).void
  }

  private[challenge] def autoStartGames: Funit =
    repo.expiredAutoStart(50).flatMap { cs =>
      lidraughts.common.Future.applySequentially(cs)(autoStart).void
    }

  private def isLimitedByMaxPlaying(c: Challenge) =
    if (c.hasClock) fuFalse
    else c.userIds.map { userId =>
      gameCache.nbPlaying(userId) map (maxPlaying <=)
    }.sequenceFu.map(_ exists identity)

  private[challenge] def sweep: Funit =
    repo.realTimeUnseenSince(DateTime.now minusSeconds 10, max = 50).flatMap { cs =>
      lidraughts.common.Future.applySequentially(cs)(offline).void
    } >>
      repo.expired(50).flatMap { cs =>
        lidraughts.common.Future.applySequentially(cs)(remove).void
      }

  private def remove(c: Challenge) =
    repo.remove(c.id) >>- uncacheAndNotify(c)

  private def uncacheAndNotify(c: Challenge): Unit = {
    c.destUserId ?? countInFor.invalidate
    c.destUserId ?? notify
    c.challengerUserId ?? notify
    socketReload(c.id)
  }

  private def socketReload(id: Challenge.ID): Unit =
    socketMap.tell(id, ChallengeSocket.Reload)

  private def notify(userId: User.ID): Funit = for {
    all <- allFor(userId)
    lang <- UserRepo langOf userId map {
      _ flatMap lidraughts.i18n.I18nLangPicker.byStr getOrElse lidraughts.i18n.defaultLang
    }
  } yield lidraughtsBus.publish(
    SendTo(userId, lidraughts.socket.Socket.makeMessage("challenges", jsonView(all, lang))),
    'socketUsers
  )
}
