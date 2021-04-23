package lidraughts.api

import akka.actor.ActorSelection

import lidraughts.hub.actorApi.Deploy

private[api] final class Cli(bus: lidraughts.common.Bus) extends lidraughts.common.Cli {

  private val logger = lidraughts.log("cli")

  def apply(args: List[String]): Fu[String] = run(args).map(_ + "\n") ~ {
    _.logFailure(logger, _ => args mkString " ") foreach { output =>
      logger.info("%s\n%s".format(args mkString " ", output))
    }
  }

  def process = {
    case "uptime" :: Nil => fuccess(s"${lidraughts.common.PlayApp.uptimeSeconds} seconds")
    case "deploy" :: "pre" :: Nil => remindDeploy(lidraughts.hub.actorApi.DeployPre)
    case "deploy" :: "post" :: Nil => remindDeploy(lidraughts.hub.actorApi.DeployPost)
    case "change" :: ("asset" | "assets") :: "version" :: Nil =>
      import lidraughts.common.AssetVersion
      AssetVersion.change
      fuccess(s"Changed to ${AssetVersion.current}")
    case "gdpr" :: "erase" :: username :: "forever" :: Nil =>
      lidraughts.user.UserRepo named username flatMap {
        case None => fuccess("No such user.")
        case Some(user) if user.enabled => fuccess("That user account is not closed. Can't erase.")
        case Some(user) => lidraughts.user.UserRepo.email(user.id) map {
          case Some(email) if email.value.toLowerCase == s"${user.id}@erase.forever" =>
            bus.publish(lidraughts.user.User.GDPRErase(user), 'gdprErase)
            s"Erasing all data about ${user.username} now"
          case _ => s"The user email must be set to <username>@erase.forever for erasing to start."
        }
      }
    case "announce" :: "cancel" :: Nil =>
      AnnounceStore set none
      bus.publish(AnnounceStore.cancel, 'announce)
      fuccess("Removed announce")
    case "announce" :: msgWords => AnnounceStore.set(msgWords mkString " ") match {
      case Some(announce) =>
        bus.publish(announce, 'announce)
        fuccess(announce.json.toString)
      case None =>
        fuccess("Invalid announce. Format: `announce <length> <unit> <words...>` or just `announce cancel` to cancel it")
    }
  }

  private def remindDeploy(event: Deploy): Fu[String] = {
    bus.publish(event, 'deploy)
    fuccess("Deploy in progress")
  }

  private def run(args: List[String]): Fu[String] = {
    (processors lift args) | fufail("Unknown command: " + args.mkString(" "))
  } recover {
    case e: Exception => "ERROR " + e
  }

  private def processors =
    lidraughts.security.Env.current.cli.process orElse
      lidraughts.i18n.Env.current.cli.process orElse
      lidraughts.teamSearch.Env.current.cli.process orElse
      lidraughts.forumSearch.Env.current.cli.process orElse
      lidraughts.team.Env.current.cli.process orElse
      lidraughts.puzzle.Env.current.cli.process orElse
      lidraughts.tournament.Env.current.cli.process orElse
      lidraughts.explorer.Env.current.cli.process orElse
      lidraughts.draughtsnet.Env.current.cli.process orElse
      lidraughts.study.Env.current.cli.process orElse
      lidraughts.studySearch.Env.current.cli.process orElse
      lidraughts.evalCache.Env.current.cli.process orElse
      lidraughts.report.Env.current.cli.process orElse
      lidraughts.game.Env.current.cli.process orElse
      lidraughts.gameSearch.Env.current.cli.process orElse
      lidraughts.plan.Env.current.cli.process orElse
      lidraughts.externalTournament.Env.current.cli.process orElse
      process
}
