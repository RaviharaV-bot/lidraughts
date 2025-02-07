package lidraughts.blog

import io.prismic.Document

import lidraughts.hub.actorApi.timeline.BlogPost
import lidraughts.timeline.EntryApi

private[blog] final class Notifier(
    blogApi: BlogApi,
    timelineApi: EntryApi
) {

  def apply(id: String): Funit =
    blogApi.prismicApi flatMap { prismicApi =>
      blogApi.one(prismicApi, none, id) flatten
        s"No such document: $id" flatMap doSend
    }

  private def doSend(post: Document): Funit = post.getText("blog.title") ?? { title =>
    timelineApi.broadcast.insert {
      BlogPost(id = post.id, slug = post.slug, title = title)
    }
  }
}
