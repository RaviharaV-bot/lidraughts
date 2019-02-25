package views.html
package round

import play.twirl.api.Html

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.game.{ Game, Pov, Player }

import controllers.routes

object bits {

  def layout(
    title: String,
    moreJs: Html = emptyHtml,
    openGraph: Option[lidraughts.app.ui.OpenGraph] = None,
    moreCss: Html = emptyHtml,
    draughtsground: Boolean = true,
    playing: Boolean = false,
    robots: Boolean = false
  )(body: Html)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      openGraph = openGraph,
      moreJs = moreJs,
      moreCss = moreCss,
      responsive = true,
      wrapClass = "full-screen",
      draughtsground = draughtsground,
      playing = playing,
      robots = robots,
      asyncJs = true,
      zoomable = true
    )(body)

  def crosstable(cross: Option[lidraughts.game.Crosstable.WithMatchup], game: Game)(implicit ctx: Context) =
    cross map { c =>
      views.html.game.crosstable(ctx.userId.fold(c)(c.fromPov), game.id.some)
    }

  def underchat(game: Game)(implicit ctx: Context) = frag(
    views.html.game.bits.watchers,
    isGranted(_.ViewBlurs) option div(cls := "round__mod")(
      game.players.filter(p => game.playerBlurPercent(p.color) > 30) map { p =>
        div(
          playerLink(p, cssClass = s"is color-icon ${p.color.name}".some, withOnline = false, mod = true),
          s"${p.blurs.nb}/${game.playerMoves(p.color)} blurs",
          strong(game.playerBlurPercent(p.color), "%")
        )
      },
      game.players flatMap { p => p.holdAlert.map(p ->) } map {
        case (p, h) => div(
          playerLink(p, cssClass = s"is color-icon ${p.color.name}".some, mod = true, withOnline = false),
          "hold alert",
          br,
          s"(ply: ${h.ply}, mean: ${h.mean} ms, SD: ${h.sd})"
        )
      }
    )
  )
}
