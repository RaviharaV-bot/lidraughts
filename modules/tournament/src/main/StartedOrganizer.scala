package lidraughts.tournament

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.Promise

import actorApi._
import makeTimeout.short

private final class StartedOrganizer(
    api: TournamentApi,
    reminder: TournamentReminder,
    socketMap: SocketMap
) extends Actor {

  override def preStart: Unit = {
    pairingLogger.info("Start StartedOrganizer")
    context setReceiveTimeout 15.seconds
    scheduleNext
  }

  case object Tick

  def scheduleNext =
    context.system.scheduler.scheduleOnce(3 seconds, self, Tick)

  def receive = {

    case ReceiveTimeout =>
      val msg = "tournament.StartedOrganizer timed out!"
      pairingLogger.error(msg)
      throw new RuntimeException(msg)

    case Tick =>
      val startAt = nowMillis
      TournamentRepo.startedTours.flatMap { started =>
        lidraughts.common.Future.traverseSequentially(started) { tour =>
          PlayerRepo activeUserIds tour.id flatMap { activeUserIds =>
            val nb = activeUserIds.size
            val result: Funit =
              if (tour.secondsToFinish <= 0) fuccess(api finish tour)
              else if (!tour.pairingsClosed && nb >= 2) startPairing(tour, activeUserIds, startAt)
              else funit
            result >>- reminder(tour, activeUserIds) inject nb
          }
        }.addEffect { playerCounts =>
          lidraughts.mon.tournament.player(playerCounts.sum)
          lidraughts.mon.tournament.started(started.size)
        }
      }.chronometer
        .mon(_.tournament.startedOrganizer.tickTime)
        .logIfSlow(500, logger)(_ => "StartedOrganizer.Tick")
        .result addEffectAnyway scheduleNext
  }

  private def startPairing(tour: Tournament, activeUserIds: List[String], startAt: Long): Funit =
    getWaitingUsers(tour) zip PairingRepo.playingUserIds(tour) map {
      case (waitingUsers, playingUserIds) =>
        val users = waitingUsers intersect activeUserIds.toSet diff playingUserIds
        api.makePairings(tour, users, startAt)
    }

  private def getWaitingUsers(tour: Tournament): Fu[WaitingUsers] =
    socketMap.askIfPresent[WaitingUsers](tour.id)(GetWaitingUsersP) map (_ | WaitingUsers.empty)
}
