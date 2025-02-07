package views.html.mod

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object menu {

  def apply(active: String)(implicit ctx: Context) = st.nav(cls := "page-menu__menu subnav")(
    isGranted(_.SeeReport) option
      a(cls := active.active("report"), href := routes.Report.list)("Reports"),
    isGranted(_.ChatTimeout) option
      a(cls := active.active("public-chat"), href := routes.Mod.publicChat)("Public Chats"),
    isGranted(_.SeeReport) option
      a(cls := active.active("gamify"), href := routes.Mod.gamify)("Hall of fame"),
    isGranted(_.UserSearch) option
      a(cls := active.active("search"), href := routes.Mod.search)("Search users"),
    isGranted(_.SetEmail) option
      a(cls := active.active("email"), href := routes.Mod.emailConfirm)("Email confirm"),
    isGranted(_.PracticeConfig) option
      a(cls := active.active("practice"), href := routes.Practice.config)("Practice"),
    isGranted(_.ManageTournament) option
      a(cls := active.active("tour"), href := routes.TournamentCrud.index(1))("Tournaments"),
    isGranted(_.ManageSimul) option
      a(cls := active.active("simul"), href := routes.SimulCrud.index(1))("Simuls"),
    isGranted(_.ManageEvent) option
      a(cls := active.active("event"), href := routes.Event.manager)("Events"),
    isGranted(_.SeeReport) option
      a(cls := active.active("log"), href := routes.Mod.log)("Mod log"),
    isGranted(_.SeeReport) option
      a(cls := active.active("irwin"), href := routes.Irwin.dashboard)("Irwin dashboard"),
    isGranted(_.Shadowban) option
      a(cls := active.active("panic"), href := routes.Mod.chatPanic)(
        "Chat Panic: ",
        strong(if (isChatPanicEnabled) "ON" else "OFF")
      ),
    isGranted(_.Settings) option
      a(cls := active.active("setting"), href := routes.Dev.settings)("Settings"),
    isGranted(_.Cli) option
      a(cls := active.active("cli"), href := routes.Dev.cli)("CLI"),
    isGranted(_.StaffForum) option
      a(cls := active.active("forum"), href := routes.ForumCateg.show(lidraughts.forum.Categ.staffId))("Staff Forum")
  )
}
