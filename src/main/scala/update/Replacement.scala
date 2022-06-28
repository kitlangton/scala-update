package update

import scala.annotation.tailrec
import scala.collection.mutable

final case class Replacement(start: Int, end: Int, string: String)

object Replacement {

  def replace(original: String, replacements: List[Replacement]): String = {
    val builder = new mutable.StringBuilder

    @tailrec
    def loop(sorted: List[Replacement], i: Int): String =
      sorted match {
        case replacement :: tail =>
          builder.append(original.substring(i, replacement.start))
          builder.append(replacement.string)
          loop(tail, replacement.end)
        case Nil =>
          builder.append(original.substring(i))
          builder.toString()
      }

    loop(replacements.sortBy(_.start).distinctBy(_.start), 0)
  }

}
