package lidraughts.study

import play.api.libs.json._
import reactivemongo.api.ReadPreference
import reactivemongo.bson._

import draughts.Color
import draughts.format.pdn.{ Tag, Tags }
import draughts.format.{ FEN, Uci }

import BSONHandlers._
import JsonView._
import lidraughts.common.MaxPerPage
import lidraughts.common.paginator.{ Paginator, PaginatorJson }
import lidraughts.db.dsl._
import lidraughts.db.paginator.{ Adapter, MapReduceAdapter }
import lidraughts.game.BSONHandlers.FENBSONHandler

final class StudyMultiBoard(
    runCommand: lidraughts.db.RunCommand,
    chapterColl: Coll,
    maxPerPage: MaxPerPage
) {

  import StudyMultiBoard._

  def json(study: Study, page: Int, playing: Boolean): Fu[JsObject] = get(study, page, playing) map { p =>
    PaginatorJson(p)
  }

  private def get(study: Study, page: Int, playing: Boolean): Fu[Paginator[ChapterPreview]] = {

    val selector = $doc("studyId" -> study.id) ++ playing.??(playingSelector)

    /* If players are found in the tags,
     * return the last mainline node.
     * Else, return the root node without its children.
     */
    Paginator(
      adapter = new MapReduceAdapter[ChapterPreview](
        collection = chapterColl,
        selector = selector,
        sort = $sort asc "order",
        runCommand = runCommand,
        command = $doc(
          "map" -> """var node = this.root, child, tagPrefixes = ['White','Black','Result'], result =
          {name:this.name,orientation:this.setup.orientation,tags:this.tags.filter(t => tagPrefixes.find(p => t.startsWith(p)))};
if (result.tags.length > 1) { while(child = node.n[0]) { node = child }; }
result.fen = node.f;
result.uci = node.u;
emit(this._id, result)""",
          "reduce" -> """function() {}""",
          "jsMode" -> true
        )
      ),
      currentPage = page,
      maxPerPage = maxPerPage
    )
  }

  private val playingSelector = $doc("tags" -> "Result:*")

  private implicit val previewBSONReader = new BSONDocumentReader[ChapterPreview] {
    def read(result: BSONDocument) = {
      val doc = result.getAs[List[Bdoc]]("value").flatMap(_.headOption) err "No mapReduce value?!"
      val tags = doc.getAs[Tags]("tags")
      ChapterPreview(
        id = result.getAs[Chapter.Id]("_id") err "Preview missing id",
        name = doc.getAs[Chapter.Name]("name") err "Preview missing name",
        players = tags flatMap ChapterPreview.players,
        orientation = doc.getAs[Color]("orientation") getOrElse Color.White,
        fen = doc.getAs[FEN]("fen") err "Preview missing FEN",
        lastMove = doc.getAs[Uci]("uci"),
        result = tags.flatMap(_(_.Result))
      )
    }
  }

  private implicit val previewPlayerWriter: Writes[ChapterPreview.Player] = Writes[ChapterPreview.Player] { p =>
    Json.obj("name" -> p.name)
      .add("title" -> p.title)
      .add("rating" -> p.rating)
  }

  private implicit val previewPlayersWriter: Writes[ChapterPreview.Players] = Writes[ChapterPreview.Players] { players =>
    Json.obj("white" -> players.white, "black" -> players.black)
  }

  private implicit val previewWriter: Writes[ChapterPreview] = Json.writes[ChapterPreview]
}

object StudyMultiBoard {

  case class ChapterPreview(
      id: Chapter.Id,
      name: Chapter.Name,
      players: Option[ChapterPreview.Players],
      orientation: Color,
      fen: FEN,
      lastMove: Option[Uci],
      result: Option[String]
  )

  object ChapterPreview {

    case class Player(name: String, title: Option[String], rating: Option[Int])

    type Players = Color.Map[Player]

    def players(tags: Tags): Option[Players] = for {
      wName <- tags(_.White)
      bName <- tags(_.Black)
    } yield Color.Map(
      white = Player(wName, tags(_.WhiteTitle), tags(_.WhiteElo) flatMap parseIntOption),
      black = Player(bName, tags(_.BlackTitle), tags(_.BlackElo) flatMap parseIntOption)
    )
  }
}
