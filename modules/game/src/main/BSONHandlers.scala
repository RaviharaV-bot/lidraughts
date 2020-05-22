package lidraughts.game

import org.joda.time.DateTime
import reactivemongo.bson._
import scala.collection.breakOut

import draughts.variant.Variant
import draughts.{ KingMoves, Color, Clock, White, Black, Status, Mode, DraughtsHistory, DraughtsGame }
import draughts.format.{ FEN, Uci }

import lidraughts.db.BSON
import lidraughts.db.dsl._

object BSONHandlers {

  import lidraughts.db.ByteArray.ByteArrayBSONHandler

  implicit val FENBSONHandler = stringAnyValHandler[FEN](_.value, FEN.apply)

  private[game] implicit val kingMovesWriter = new BSONWriter[KingMoves, BSONArray] {
    def write(km: KingMoves) = BSONArray(km.white, km.black, km.whiteKing.fold(0)(_.fieldNumber), km.blackKing.fold(0)(_.fieldNumber))
  }

  implicit val StatusBSONHandler = new BSONHandler[BSONInteger, Status] {
    def read(bsonInt: BSONInteger): Status = Status(bsonInt.value) err s"No such status: ${bsonInt.value}"
    def write(x: Status) = BSONInteger(x.id)
  }

  import Player.playerBSONHandler
  private val emptyPlayerBuilder = playerBSONHandler.read($empty)

  implicit val gameBSONHandler: BSON[Game] = new BSON[Game] {

    import Game.{ BSONFields => F }
    import PdnImport.pdnImportBSONHandler

    def reads(r: BSON.Reader): Game = {

      lidraughts.mon.game.fetch()

      val light = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val gameVariant = Variant(r intD F.variant) | draughts.variant.Standard
      val startedAtTurn = r intD F.startedAtTurn
      val plies = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val playedPlies = plies - startedAtTurn

      val decoded = r.bytesO(F.huffmanPdn).map { PdnStorage.Huffman.decode(_, playedPlies) } | {
        PdnStorage.Decoded(
          pdnMoves = PdnStorage.OldBin.decode(r bytesD F.oldPdn, playedPlies),
          pieces = BinaryFormat.piece.read(r bytes F.binaryPieces, gameVariant),
          positionHashes = r.getO[draughts.PositionHash](F.positionHashes) | Array.empty,
          lastMove = r strO F.historyLastMove flatMap Uci.apply,
          format = PdnStorage.OldBin
        )
      }

      val decodedBoard = draughts.Board(
        pieces = decoded.pieces,
        history = DraughtsHistory(
          lastMove = decoded.lastMove,
          positionHashes = decoded.positionHashes,
          kingMoves = if (gameVariant.frisianVariant) {
            val counts = r.intsD(F.kingMoves)
            KingMoves(~counts.headOption, ~counts.tailOption.flatMap(_.headOption), if (counts.length > 2) draughts.Pos.posAt(counts(2)) else none, if (counts.length > 3) draughts.Pos.posAt(counts(3)) else none)
          } else Game.emptyKingMoves,
          variant = gameVariant
        ),
        variant = gameVariant
      )

      val midCapture = decoded.pdnMoves.lastOption.fold(false)(_.indexOf('x') != -1) && decodedBoard.ghosts != 0
      val currentPly = if (midCapture) plies - 1 else plies
      val turnColor = Color.fromPly(currentPly)

      val decodedSituation = draughts.Situation(
        board = decodedBoard,
        color = turnColor
      )

      val createdAt = r date F.createdAt

      val draughtsGame = DraughtsGame(
        situation = decodedSituation,
        pdnMoves = decoded.pdnMoves,
        clock = r.getO[Color => Clock](F.clock) {
          clockBSONReader(createdAt, light.whitePlayer.berserk, light.blackPlayer.berserk)
        } map (_(decodedSituation.color)),
        turns = currentPly,
        startedAtTurn = startedAtTurn
      )

      Game(
        id = r str F.id,
        whitePlayer = light.whitePlayer,
        blackPlayer = light.blackPlayer,
        draughts = draughtsGame,
        loadClockHistory = clk => for {
          bw <- r bytesO F.whiteClockHistory
          bb <- r bytesO F.blackClockHistory
          history <- BinaryFormat.clockHistory.read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(decodedSituation.color))
          _ = lidraughts.mon.game.loadClockHistory()
        } yield history,
        pdnStorage = decoded.format,
        status = light.status,
        daysPerTurn = r intO F.daysPerTurn,
        binaryMoveTimes = r bytesO F.moveTimes,
        mode = Mode(r boolD F.rated),
        next = r strO F.next,
        bookmarks = r intD F.bookmarks,
        createdAt = createdAt,
        movedAt = r.dateD(F.movedAt, createdAt),
        metadata = Metadata(
          source = r intO F.source flatMap Source.apply,
          pdnImport = r.getO[PdnImport](F.pdnImport)(PdnImport.pdnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          simulId = r strO F.simulId,
          simulPairing = r intO F.simulPairing,
          timeOutUntil = r dateO F.timeOutUntil,
          drawLimit = r intO F.drawLimit,
          analysed = r boolD F.analysed
        )
      )
    }

    def writes(w: BSON.Writer, o: Game) = BSONDocument(
      F.id -> o.id,
      F.playerIds -> (o.whitePlayer.id + o.blackPlayer.id),
      F.playerSris -> w.strListO(List(~o.whitePlayer.userId, ~o.blackPlayer.userId)),
      F.whitePlayer -> w.docO(playerBSONHandler write ((_: Color) => (_: Player.ID) => (_: Player.UserId) => (_: Player.Win) => o.whitePlayer)),
      F.blackPlayer -> w.docO(playerBSONHandler write ((_: Color) => (_: Player.ID) => (_: Player.UserId) => (_: Player.Win) => o.blackPlayer)),
      F.status -> o.status,
      F.turns -> o.draughts.turns,
      F.startedAtTurn -> w.intO(o.draughts.startedAtTurn),
      F.clock -> (o.draughts.clock map { c => clockBSONWrite(o.createdAt, c) }),
      F.daysPerTurn -> o.daysPerTurn,
      F.moveTimes -> o.binaryMoveTimes,
      F.whiteClockHistory -> clockHistory(White, o.clockHistory, o.draughts.clock, o.flagged),
      F.blackClockHistory -> clockHistory(Black, o.clockHistory, o.draughts.clock, o.flagged),
      F.rated -> w.boolO(o.mode.rated),
      F.variant -> o.board.variant.exotic.option(w int o.board.variant.id),
      F.next -> o.next,
      F.bookmarks -> w.intO(o.bookmarks),
      F.createdAt -> w.date(o.createdAt),
      F.movedAt -> w.date(o.movedAt),
      F.source -> o.metadata.source.map(_.id),
      F.pdnImport -> o.metadata.pdnImport,
      F.tournamentId -> o.metadata.tournamentId,
      F.simulId -> o.metadata.simulId,
      F.simulPairing -> o.metadata.simulPairing,
      F.timeOutUntil -> o.metadata.timeOutUntil.map(w.date),
      F.drawLimit -> o.metadata.drawLimit,
      F.analysed -> w.boolO(o.metadata.analysed)
    ) ++ {
        o.pdnStorage match {
          case f @ PdnStorage.OldBin => $doc(
            F.oldPdn -> f.encode(o.pdnMoves take Game.maxPlies),
            F.binaryPieces -> BinaryFormat.piece.write(o.board.pieces),
            F.positionHashes -> o.history.positionHashes,
            F.historyLastMove -> o.history.lastMove.map(_.uci),
            // since variants are always OldBin
            F.kingMoves -> o.history.kingMoves.nonEmpty.option(o.history.kingMoves)
          )
          case f @ PdnStorage.Huffman => $doc(
            F.huffmanPdn -> f.encode(o.pdnMoves take Game.maxPlies)
          )
        }
      }
  }

  implicit val lightGameBSONHandler = new lidraughts.db.BSONReadOnly[LightGame] {

    import Game.{ BSONFields => F }
    import Player.playerBSONHandler

    def reads(r: BSON.Reader): LightGame = {
      lidraughts.mon.game.fetchLight()
      readsWithPlayerIds(r, "")
    }

    def readsWithPlayerIds(r: BSON.Reader, playerIds: String): LightGame = {
      val (whiteId, blackId) = playerIds splitAt 4
      val winC = r boolO F.winnerColor map Color.apply
      val sris = ~r.getO[List[lidraughts.user.User.ID]](F.playerSris)
      val (whiteSri, blackSri) = (sris.headOption.filter(_.nonEmpty), sris.lift(1).filter(_.nonEmpty))
      def makePlayer(field: String, color: Color, id: Player.ID, sri: Player.UserId): Player = {
        val builder = r.getO[Player.Builder](field)(playerBSONHandler) | emptyPlayerBuilder
        val win = winC map (_ == color)
        builder(color)(id)(sri)(win)
      }
      LightGame(
        id = r str F.id,
        whitePlayer = makePlayer(F.whitePlayer, White, whiteId, whiteSri),
        blackPlayer = makePlayer(F.blackPlayer, Black, blackId, blackSri),
        status = r.get[Status](F.status)
      )
    }
  }

  private def clockHistory(color: Color, clockHistory: Option[ClockHistory], clock: Option[Clock], flagged: Option[Color]) =
    for {
      clk <- clock
      history <- clockHistory
      times = history(color)
    } yield BinaryFormat.clockHistory.writeSide(clk.limit, times, flagged has color)

  private[game] def clockBSONReader(since: DateTime, whiteBerserk: Boolean, blackBerserk: Boolean) = new BSONReader[BSONBinary, Color => Clock] {
    def read(bin: BSONBinary) = BinaryFormat.clock(since).read(
      ByteArrayBSONHandler read bin, whiteBerserk, blackBerserk
    )
  }

  private[game] def clockBSONWrite(since: DateTime, clock: Clock) = ByteArrayBSONHandler write {
    BinaryFormat clock since write clock
  }
}
