package dependencies.versions

import coursier.util.Sync
import zio.{Task, ZIO}

import java.util.concurrent.ExecutorService
import scala.concurrent.ExecutionContext

object ZioSyncInstance {

  implicit lazy val taskSync: Sync[Task] = new Sync[Task] {
    override def delay[A](a: => A): Task[A] = ZIO.attempt(a)

    override def handle[A](a: Task[A])(f: PartialFunction[Throwable, A]): Task[A] =
      a.catchSome { case f(e) => ZIO.succeed(e) }

    override def fromAttempt[A](a: Either[Throwable, A]): Task[A] =
      ZIO.fromEither(a)

    override def gather[A](elems: Seq[Task[A]]): Task[Seq[A]] =
      ZIO.collectAll(elems)

    override def point[A](a: A): Task[A] = ZIO.succeed(a)

    override def bind[A, B](elem: Task[A])(f: A => Task[B]): Task[B] =
      elem.flatMap(f)

    override def schedule[A](pool: ExecutorService)(f: => A): Task[A] =
      ZIO.attempt(f).onExecutionContext(ExecutionContext.fromExecutor(pool))
  }
}
