package lidraughts.swiss

import lidraughts.db.dsl._

final class SwissScoring(
    playerColl: Coll,
    pairingColl: Coll
) {

  import BsonHandlers._

  def recompute(swiss: Swiss): Funit =
    SwissPlayer
      .fields { p =>
        for {
          prevPlayers <- fetchPlayers(swiss)
          pairings <- fetchPairings(swiss)
          pairingMap = SwissPairing.toMap(pairings)
          playersWithPoints = prevPlayers.map { p =>
            val playerPairings = ~pairingMap.get(p.number)
            p.copy(
              points = swiss.allRounds.pp.foldLeft(Swiss.Points(0)) {
                case (points, round) =>
                  points + playerPairings
                    .get(round)
                    .fold(Swiss.Points(1)) { pairing =>
                      pairing.winner.map(p.number.==) match {
                        case Some(true) => Swiss.Points(2)
                        case None => Swiss.Points(1)
                        case _ => Swiss.Points(0)
                      }
                    }
                    .pp
              }
            )
          }
          playerMap = SwissPlayer.toMap(playersWithPoints)
          players = playersWithPoints.map { p =>
            p.copy(score = Swiss.Score {
              (~pairingMap.get(p.number)).values.foldLeft(0d) {
                case (score, pairing) =>
                  score + {
                    def opponentPoints = playerMap.get(pairing opponentOf p.number).??(_.points.value)
                    pairing.winner.map(p.number.==) match {
                      case Some(true) => opponentPoints
                      case None => opponentPoints / 2
                      case _ => 0
                    }
                  }
              }
            })
          }
          _ <- SwissPlayer.fields { f =>
            prevPlayers
              .zip(players.pp)
              .map {
                case (prev, player) =>
                  val upd = (prev.points != player.points).?? { $doc(f.points -> player.points) } ++
                    (prev.score != player.score).?? { $doc(f.score -> player.score) }
                  println(lidraughts.db.BSON.debug(upd))
                  !upd.isEmpty ?? playerColl.update($id(p.id), $set(upd)).void
              }
              .sequenceFu
              .void
          }
        } yield {}
      }

  private def fetchPlayers(swiss: Swiss) = SwissPlayer.fields { f =>
    playerColl
      .find($doc(f.swissId -> swiss.id))
      .sort($sort asc f.number)
      .list[SwissPlayer]()
  }

  private def fetchPairings(swiss: Swiss) = SwissPairing.fields { f =>
    pairingColl
      .find($doc(f.swissId -> swiss.id))
      .list[SwissPairing]()
  }
}
