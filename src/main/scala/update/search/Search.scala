package update.search

import update.{Artifact, Group, Version}
import view.View
import zio._
import zio.json._

import java.net.URLEncoder
import java.time.Instant
import scala.annotation.tailrec

final case class Payload(
  response: Response
)

object Payload {
  implicit val codec: JsonCodec[Payload] =
    DeriveJsonCodec.gen[Payload]
}

final case class Response(
  numFound: Int,
  start: Int,
  docs: List[Doc]
)

object Response {
  implicit val codec: JsonCodec[Response] =
    DeriveJsonCodec.gen[Response]
}

final case class Doc(
  id: String,
  g: String,
  a: String,
  latestVersion: String,
  timestamp: Long
)

final case class SearchResult(
  group: Group,
  artifact: Artifact,
  scalaVersions: List[String],
  latestVersion: Version,
  lastUpdated: Instant
) {
  def render: View =
    View.horizontal(
      View.text(s"\"${group.value}\"").cyan,
      View.text("%%").cyan.dim,
      View.text(s"\"${artifact.value}\"").cyan,
      View.text("%").cyan.dim,
      View.text(s"\"${latestVersion.value}\"").cyan
    )
}

object SearchResult {
  def fromDoc(doc: Doc): SearchResult = {
    val (artifact, scalaVersion) =
      doc.a match {
        case s"${artifact}_sjs1_2.11" =>
          (artifact, "sjs1_2.11")
        case s"${artifact}_sjs1_2.12" =>
          (artifact, "sjs1_2.12")
        case s"${artifact}_sjs1_2.13" =>
          (artifact, "sjs1_2.13")
        case s"${artifact}_sjs1_3" =>
          (artifact, "sjs1_3")
        case s"${artifact}_2.11" =>
          (artifact, "2.11")
        case s"${artifact}_2.12" =>
          (artifact, "2.12")
        case s"${artifact}_2.13" =>
          (artifact, "2.13")
        case s"${artifact}_3" =>
          (artifact, "3")
        case other =>
          (other, "OOPS")
      }

    SearchResult(
      Group(doc.g),
      Artifact(artifact),
      List(scalaVersion),
      Version(doc.latestVersion),
      Instant.ofEpochMilli(doc.timestamp)
    )
  }

  // Combine results with same group, artifact and latestVersion but different scala versions
  def combineResults(results: List[SearchResult]): List[SearchResult] = {
    @tailrec
    def loop(
      results: List[SearchResult],
      acc: List[SearchResult]
    ): List[SearchResult] =
      (results, acc) match {
        case (Nil, acc) => acc.reverse
        case (r :: rs, a :: as)
            if a.group == r.group && a.artifact == r.artifact && a.latestVersion == r.latestVersion =>
          loop(rs, a.copy(scalaVersions = a.scalaVersions ++ r.scalaVersions) :: as)
        case (r :: rs, acc) =>
          loop(rs, r :: acc)
      }

    loop(results, Nil)
  }

}

object Doc {
  implicit val codec: JsonCodec[Doc] =
    DeriveJsonCodec.gen[Doc]
}

final case class Search() {

  def search(query: String): Task[List[SearchResult]] = {
    val urlEncodedQuery = URLEncoder.encode(query, "UTF-8")
    val url             = s"https://search.maven.org/solrsearch/select?q=$urlEncodedQuery&start=0&rows=60"
    for {
      string <- ZIO.attempt {
                  val source = scala.io.Source.fromURL(url)
                  val string = source.mkString
                  source.close()
                  string
                }
      payload <- ZIO.from(string.fromJson[Payload]).mapError(new Error(_))
    } yield SearchResult
      .combineResults(payload.response.docs.map(SearchResult.fromDoc))
      .distinctBy(sr => (sr.group, sr.artifact, sr.latestVersion))
  }

  def searchCLI(query: String): Task[Unit] =
    for {
      results <- search(query)
      _ <- ZIO.debug(
             View
               .vertical(
                 Chunk(
                   View.horizontal(View.text("MAVEN PACKAGES FOR").blue, View.text(query).blue.underlined),
                   View.text("─" * s"MAVEN PACKAGES FOR $query".length).blue.dim
                 ) ++
                   results.map(_.render): _*
               )
               .padding(1)
               .renderNow
           )
    } yield ()

}
