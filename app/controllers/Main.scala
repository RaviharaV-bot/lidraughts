package controllers

import akka.pattern.ask
import play.api.data._, Forms._
import play.api.libs.json._
import play.api.mvc._

import lidraughts.app._
import lidraughts.api.Context
import lidraughts.common.HTTPRequest
import lidraughts.hub.actorApi.captcha.ValidCaptcha
import makeTimeout.large
import views._

object Main extends LidraughtsController {

  private lazy val blindForm = Form(tuple(
    "enable" -> nonEmptyText,
    "redirect" -> nonEmptyText
  ))

  def toggleBlindMode = OpenBody { implicit ctx =>
    implicit val req = ctx.body
    fuccess {
      blindForm.bindFromRequest.fold(
        err => BadRequest, {
          case (enable, redirect) =>
            Redirect(redirect) withCookies lidraughts.common.LidraughtsCookie.cookie(
              Env.api.Accessibility.blindCookieName,
              if (enable == "0") "" else Env.api.Accessibility.hash,
              maxAge = Env.api.Accessibility.blindCookieMaxAge.some,
              httpOnly = true.some
            )
        }
      )
    }
  }

  def websocket(apiVersion: Int) = SocketOption { implicit ctx =>
    getSocketSri("sri") ?? { sri =>
      Env.site.socketHandler.human(sri, ctx.userId, apiVersion, get("flag")) map some
    }
  }

  def apiWebsocket = WebSocket.tryAccept { req =>
    Env.site.socketHandler.api(lidraughts.api.Mobile.Api.currentVersion) map Right.apply
  }

  def captchaCheck(id: String) = Open { implicit ctx =>
    Env.hub.captcher ? ValidCaptcha(id, ~get("solution")) map {
      case valid: Boolean => Ok(if (valid) 1 else 0)
    }
  }

  def webmasters = Open { implicit ctx =>
    pageHit
    fuccess {
      html.site.help.webmasters()
    }
  }

  def lag = Open { implicit ctx =>
    pageHit
    fuccess {
      html.site.lag()
    }
  }

  def mobile = Open { implicit ctx =>
    pageHit
    OptionOk(Prismic getBookmark "mobile-apk") {
      case (doc, resolver) => html.mobile(doc, resolver)
    }
  }

  def jslog(id: String) = Open { ctx =>
    Env.round.selfReport(
      userId = ctx.userId,
      ip = HTTPRequest lastRemoteAddress ctx.req,
      fullId = id,
      name = get("n", ctx.req) | "?"
    )
    NoContent.fuccess
  }

  /**
   * Event monitoring endpoint
   */
  def jsmon(event: String) = Action {
    if (event == "socket_gap") lidraughts.mon.jsmon.socketGap()
    else lidraughts.mon.jsmon.unknown()
    NoContent
  }

  private lazy val glyphsResult: Result = {
    import draughts.format.pdn.Glyph
    import lidraughts.tree.Node.glyphWriter
    Ok(Json.obj(
      "move" -> Glyph.MoveAssessment.display,
      "position" -> Glyph.PositionAssessment.display,
      "observation" -> Glyph.Observation.display
    )) as JSON
  }
  val glyphs = Action(glyphsResult)

  def image(id: String, hash: String, name: String) = Action.async { req =>
    Env.db.image.fetch(id) map {
      case None => NotFound
      case Some(image) =>
        lidraughts.log("image").info(s"Serving ${image.path} to ${HTTPRequest printClient req}")
        Ok(image.data).withHeaders(
          CONTENT_TYPE -> image.contentType.getOrElse("image/jpeg"),
          CONTENT_DISPOSITION -> image.name,
          CONTENT_LENGTH -> image.size.toString
        )
    }
  }

  val robots = Action { req =>
    Ok {
      if (Env.api.Net.Crawlable && req.domain == Env.api.Net.Domain) """User-agent: *
Allow: /
Disallow: /game/export
Disallow: /games/export
"""
      else "User-agent: *\nDisallow: /"
    }
  }

  def renderNotFound(req: RequestHeader): Fu[Result] =
    reqToCtx(req) map renderNotFound

  def renderNotFound(ctx: Context): Result = {
    lidraughts.mon.http.response.code404()
    NotFound(html.base.notFound()(ctx))
  }

  def getDraughtsnet = Open { implicit ctx =>
    Ok(html.site.bits.getDraughtsnet()).fuccess
  }

  def faq = Open { implicit ctx =>
    Ok(html.site.faq()).fuccess
  }

  def legacyQa = Open { implicit ctx =>
    MovedPermanently(routes.Main.faq.url).fuccess
  }

  def legacyQaQuestion(id: Int, slug: String) = Open { implicit ctx =>
    MovedPermanently {
      val faq = routes.Main.faq.url
      id match {
        case 1 => s"$faq#titles"
        case 2 => routes.Stat.ratingDistribution("blitz").url
        case 11 | 18 => routes.Main.mobile.url
        case 19 => s"$faq#timeout"
        case 38 => "https://lidraughts.org/forum/lidraughts-feedback/a-text-file-for-tournaments-as-available-on-playok"
        case _ => faq
      }
    }.fuccess
  }

  def versionedAsset(version: String, file: String) = Assets.at(path = "/public", file)
}
