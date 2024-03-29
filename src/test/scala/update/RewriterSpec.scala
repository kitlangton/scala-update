package update

import update.utils.Rewriter
import update.utils.Rewriter.Patch
import zio.test.*

object RewriterSpec extends ZIOSpecDefault:
  def spec =
    suiteAll("Rewriter") {
      test("rewrite with a single patch") {
        val source  = "Hello World"
        val patches = List(Patch(6, 11, "Scala"))
        val result  = Rewriter.rewrite(source, patches)
        assertTrue(result == "Hello Scala")
      }

      test("rewrite with multiple non-overlapping patches") {
        val source  = "Hello World"
        val patches = List(Patch(0, 5, "Hi"), Patch(6, 11, "Scala"))
        val result  = Rewriter.rewrite(source, patches)
        assertTrue(result == "Hi Scala")
      }

      // this should throw an error
      test("rewrite with overlapping patches") {
        val source  = "Hello World"
        val patches = List(Patch(0, 11, "Bonjour"), Patch(6, 11, "Scala"))

        try
          val result = Rewriter.rewrite(source, patches)
          assertTrue(false)
        catch
          case e: Exception =>
            assertTrue(e.getMessage.contains("Overlapping patches"))
      }

      test("rewrite with patches at the beginning and end of source") {
        val source  = "Hello World"
        val patches = List(Patch(0, 1, "J"), Patch(10, 11, "d!"))
        val result  = Rewriter.rewrite(source, patches)
        assertTrue(result == "Jello World!")
      }

      test("rewrite with empty replacement") {
        val source  = "Hello World"
        val patches = List(Patch(5, 6, ""))
        val result  = Rewriter.rewrite(source, patches)
        assertTrue(result == "HelloWorld")
      }

      test("rewrite with no patches") {
        val source  = "Hello World"
        val patches = List()
        val result  = Rewriter.rewrite(source, patches)
        assertTrue(result == "Hello World")
      }

      test("rewrite with a patch that replaces the entire source") {
        val source  = "Hello World"
        val patches = List(Patch(0, source.length, "Goodbye"))
        val result  = Rewriter.rewrite(source, patches)
        assertTrue(result == "Goodbye")
      }
    }
