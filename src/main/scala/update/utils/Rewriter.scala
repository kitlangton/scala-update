package update.utils

object Rewriter:
  final case class Patch(start: Int, end: Int, replacement: String):
    def length: Int = end - start

  /** This function rewrites a given source string based on a list of patches.
    * Each patch contains a start and end index, and a replacement string. The
    * function applies these patches in order of their start index. If any
    * patches overlap (i.e., a patch starts before the previous one ends), an
    * exception is thrown.
    */
  def rewrite(source: String, patches: List[Patch]): String =
    val sortedPatches = patches.sortBy(_.start)

    val sb      = new StringBuilder
    var lastEnd = 0

    // Apply each patch in order
    sortedPatches.foreach { patch =>
      // If a patch starts before the last one ends, throw an exception
      if patch.start < lastEnd then
        throw new IllegalArgumentException(
          s"Overlapping patches: $patch and ${sortedPatches.find(_.start < lastEnd).get}"
        )

      // Append the part of the source string before the patch
      try sb.append(source.substring(lastEnd, patch.start))
      catch
        case e: StringIndexOutOfBoundsException =>
          throw new IllegalArgumentException(
            s"Patch $patch starts at index ${patch.start} but source string has length ${source.length}"
          )
      // Append the replacement string
      sb.append(patch.replacement)
      // Update the end index of the last applied patch
      lastEnd = patch.end
    }

    // Append the part of the source string after the last patch
    sb.append(source.substring(lastEnd))
    sb.toString
