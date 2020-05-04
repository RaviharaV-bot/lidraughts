package lidraughts.swiss

import lidraughts.rating.Perf
import lidraughts.user.{ Perfs, User }

case class SwissPlayer(
    id: SwissPlayer.Id, // random
    swissId: Swiss.Id,
    number: SwissPlayer.Number,
    userId: User.ID,
    rating: Int,
    provisional: Boolean,
    points: Swiss.Points,
    tieBreak: Swiss.TieBreak,
    performance: Option[Swiss.Performance],
    score: Swiss.Score
) {
  def is(uid: User.ID): Boolean = uid == userId
  def is(user: User): Boolean = is(user.id)
  def is(other: SwissPlayer): Boolean = is(other.userId)

  def recomputeScore = copy(
    score = Swiss.makeScore(points, tieBreak, performance | Swiss.Performance(rating.toFloat))
  )
}

object SwissPlayer {

  case class Id(value: String) extends AnyVal with StringValue

  def makeId(swissId: Swiss.Id, userId: User.ID) = Id(s"$swissId:$userId")

  private[swiss] def make(
    swissId: Swiss.Id,
    number: SwissPlayer.Number,
    user: User,
    perfLens: Perfs => Perf
  ): SwissPlayer =
    new SwissPlayer(
      id = makeId(swissId, user.id),
      swissId = swissId,
      number = number,
      userId = user.id,
      rating = perfLens(user.perfs).intRating,
      provisional = perfLens(user.perfs).provisional,
      points = Swiss.Points(0),
      tieBreak = Swiss.TieBreak(0),
      performance = none,
      score = Swiss.Score(0)
    ).recomputeScore

  case class Number(value: Int) extends AnyVal with IntValue

  case class Ranked(rank: Int, player: SwissPlayer) {
    def is(other: Ranked) = player is other.player
    override def toString = s"$rank. ${player.userId}[${player.rating}]"
  }

  def toMap(players: List[SwissPlayer]): Map[SwissPlayer.Number, SwissPlayer] =
    players.view.map(p => p.number -> p).toMap

  // def ranked(ranking: Ranking)(player: SwissPlayer): Option[Ranked] =
  //   ranking get player.userId map { rank =>
  //     Ranked(rank + 1, player)
  //   }

  object Fields {
    val id = "_id"
    val swissId = "s"
    val number = "n"
    val userId = "u"
    val rating = "r"
    val provisional = "pr"
    val points = "p"
    val tieBreak = "t"
    val performance = "e"
    val score = "c"
  }
  def fields[A](f: Fields.type => A): A = f(Fields)
}
