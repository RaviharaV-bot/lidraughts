package lidraughts.i18n

import play.api.mvc.RequestHeader

import lidraughts.common.Lang

object I18nLangPicker {

  def apply(req: RequestHeader, userLang: Option[String]): Lang =
    userLang
      .orElse(req.session get "lang")
      .flatMap(Lang.get)
      .flatMap(findCloser)
      .orElse(bestFromRequestHeaders(req))
      .getOrElse(defaultLang)

  def bestFromRequestHeaders(req: RequestHeader): Option[Lang] =
    req.acceptLanguages.foldLeft(none[Lang]) {
      case (None, lang) => findCloser(Lang(lang))
      case (found, _) => found
    }

  def allFromRequestHeaders(req: RequestHeader): List[Lang] =
    req.acceptLanguages.flatMap(l => findCloser(Lang(l))).distinct.toList

  def byStr(str: String): Option[Lang] =
    Lang get str flatMap findCloser

  private val defaultByLanguage: Map[String, Lang] =
    I18nDb.langs.foldLeft(Map.empty[String, Lang]) {
      case (acc, lang) => acc + (lang.language -> lang)
    }

  def findCloser(to: Lang): Option[Lang] =
    if (I18nDb.langs contains to) Some(to)
    else defaultByLanguage.get(to.language) orElse
      lidraughtsCodes.get(to.language)
}
