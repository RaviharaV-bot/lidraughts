package lidraughts.externalTournament

import lidraughts.common.LightFmjdUser
import lidraughts.user.User

private[externalTournament] case class FmjdPlayer(
    _id: FmjdPlayer.ID,
    userId: Option[String],
    firstName: String,
    lastName: String,
    country: String,
    title: Option[String],
    rating: Option[Int],
    titleW: Option[String],
    ratingW: Option[Int]
) {

  import FmjdPlayer._

  def id = _id

  def is(uid: User.ID): Boolean = userId.contains(uid)
  def is(user: User): Boolean = is(user.id)

  def displayName = toDisplayName(firstName, lastName)
  def bestTitle = if (title.isDefined) title else titleW

  def toLightFmjdUser = LightFmjdUser(
    id = id,
    name = displayName,
    title = bestTitle
  )
}

private[externalTournament] object FmjdPlayer {

  type ID = String

  def toDisplayName(firstName: String, lastName: String) =
    s"${lastName.capitalize}, ${firstName.capitalize}"
}