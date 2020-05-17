package lidraughts.app
package templating

import scala.concurrent.duration._

import lidraughts.api.Context
import lidraughts.api.Env.{ current => apiEnv }
import lidraughts.app.ui.ScalatagsTemplate._

object Environment
  extends lidraughts.Lidraughtsisms
  with StringHelper
  with AssetHelper
  with DateHelper
  with NumberHelper
  with PaginatorHelper
  with FormHelper
  with SetupHelper
  with AiHelper
  with GameHelper
  with UserHelper
  with ForumHelper
  with I18nHelper
  with SecurityHelper
  with TeamHelper
  with TournamentHelper
  with DraughtsgroundHelper {

  type FormWithCaptcha = (play.api.data.Form[_], lidraughts.common.Captcha)

  def netDomain = apiEnv.Net.Domain
  def netBaseUrl = apiEnv.Net.BaseUrl
  val isGloballyCrawlable = apiEnv.Net.Crawlable

  def isProd = apiEnv.isProd
  def isStage = apiEnv.isStage

  def apiVersion = lidraughts.api.Mobile.Api.currentVersion

  def explorerEndpoint = apiEnv.ExplorerEndpoint

  def tablebaseEndpoint = apiEnv.TablebaseEndpoint

  def contactEmail = apiEnv.Net.Email

  def contactEmailLink = a(href := s"mailto:$contactEmail")(contactEmail)

  def reportNbOpen: Int =
    lidraughts.report.Env.current.api.nbOpen.awaitOrElse(10.millis, 0)

  def cspEnabled = apiEnv.cspEnabledSetting.get _

  def isChatPanicEnabled =
    lidraughts.chat.Env.current.panic.enabled

  def NotForKids(f: => Frag)(implicit ctx: Context) = if (ctx.kid) emptyFrag else f

  val spinner: Frag = raw("""<div class="spinner"><svg viewBox="0 0 40 40"><circle cx=20 cy=20 r=18 fill="none"></circle></svg></div>""")

  def maybeRemoteSocketDomain(implicit ctx: Context): Option[String] = {
    val regex = Env.socket.socketRemoteUsersSetting.get()
    regex == "." || ctx.userId.exists(regex.r.matches)
  } option remoteSocketDomain
}
