package lila.socket

import play.api.libs.json._
import redis.clients.jedis._
import scala.concurrent.Future

import lila.hub.actorApi.round.{ MoveEvent, FinishGameId }
import lila.hub.actorApi.socket.{ SendTo, SendTos }

private final class RemoteSocket(
    makeRedis: () => Jedis,
    chanIn: String,
    chanOut: String,
    lifecycle: play.api.inject.ApplicationLifecycle,
    bus: lila.common.Bus
) {

  private val clientIn = makeRedis()
  private val clientOut = makeRedis()

  Future {
    clientIn.subscribe(new JedisPubSub() {
      override def onMessage(channel: String, message: String): Unit = {
        try {
          Json.parse(message) match {
            case o: JsObject => for {
              path <- o str "path"
              data <- o obj "data"
            } onReceive(path, data)
            case _ => logger warn s"Invalid message $message"
          }
        } catch {
          case _: Exception => logger.warn(s"Can't parse remote socket message $message")
        }
      }
    }, chanIn)
  }

  lifecycle.addStopHook { () =>
    logger.info("Stopping the Redis clients...")
    Future {
      clientIn.quit()
      clientOut.quit()
    }
  }

  private val connectedUserIds = collection.mutable.Set.empty[String]
  private val watchedGameIds = collection.mutable.Set.empty[String]

  bus.subscribeFun('moveEvent, 'finishGameId, 'socketUsers) {
    case MoveEvent(gameId, fen, move) if watchedGameIds(gameId) => send(Json.obj(
      "path" -> "/move",
      "data" -> Json.obj(
        "gameId" -> gameId,
        "fen" -> fen,
        "move" -> move
      )
    ))
    case FinishGameId(gameId) if watchedGameIds(gameId) => watchedGameIds -= gameId
    case SendTos(userIds, payload) =>
      val connectedUsers = userIds intersect connectedUserIds
      if (connectedUsers.nonEmpty) send(Json.obj(
        "path" -> "/tell/users",
        "data" -> Json.obj(
          "users" -> connectedUsers,
          "payload" -> payload
        )
      ))
    case SendTo(userId, payload) if connectedUserIds(userId) =>
      send(Json.obj(
        "path" -> "/tell/user",
        "data" -> Json.obj(
          "user" -> userId,
          "payload" -> payload
        )
      ))
  }

  private def onReceive(path: String, data: JsObject) = path match {
    case "/connect" => data str "user" foreach { user =>
      println(s"connect $user")
      connectedUserIds += user
    }
    case "/disconnect" => data str "user" foreach { user =>
      println(s"disconnect $user")
      connectedUserIds -= user
    }
    case "/watch" => data str "game" foreach { gameId =>
      watchedGameIds += gameId
    }
    case path => logger.warn(s"Invalid path $path")
  }

  private def send(data: JsObject) = clientOut.publish(chanOut, Json stringify data)
}
