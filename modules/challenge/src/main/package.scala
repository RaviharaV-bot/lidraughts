package lidraughts

import org.joda.time.DateTime

import lidraughts.socket.WithSocket

package object challenge extends PackageObject with WithSocket {

  type EitherChallenger = Either[Challenge.Anonymous, Challenge.Registered]

  private[challenge] type SocketMap = lidraughts.hub.TrouperMap[ChallengeSocket]

  private[challenge] def inTwoWeeks = DateTime.now plusWeeks 2

  private[challenge] val logger = lidraughts.log("challenge")
}
