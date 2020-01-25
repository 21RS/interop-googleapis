package zio.interop

import java.util.concurrent.{CompletionException, Future}

import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import zio._

import scala.concurrent.{ExecutionContext, ExecutionException}

object googleapis {

  private def catchFromGet(isFatal: Throwable => Boolean): PartialFunction[Throwable, Task[Nothing]] = {
    case e: CompletionException =>
      Task.fail(e.getCause)
    case e: ExecutionException =>
      Task.fail(e.getCause)
    case _: InterruptedException =>
      Task.interrupt
    case e if !isFatal(e) =>
      Task.fail(e)
  }

  private def unwrapDone[A](isFatal: Throwable => Boolean)(f: Future[A]): Task[A] =
    try Task.succeed(f.get())
    catch catchFromGet(isFatal)

  def fromApiFuture[A](make: ExecutionContext => ApiFuture[A]): Task[A] =
    Task.descriptorWith { d =>
      Task.effectSuspendWith { p =>
        val ec = d.executor.asEC
        val lf = make(ec)
        if (lf.isDone)
          unwrapDone(p.fatal)(lf)
        else
          Task.effectAsync { cb =>
            val fcb = new ApiFutureCallback[A] {
              def onFailure(t: Throwable): Unit = cb(catchFromGet(p.fatal).lift(t).getOrElse(Task.die(t)))
              def onSuccess(result: A): Unit    = cb(Task.succeed(result))
            }
            ApiFutures.addCallback(lf, fcb, ec.execute(_))
          }
      }
    }

  def fromApiFuture[A](lfUio: UIO[ApiFuture[A]]): Task[A] =
    lfUio.flatMap(lf => fromApiFuture(_ => lf))

  implicit class ApiFutureOps[A](private val lfUio: UIO[ApiFuture[A]]) extends AnyVal {
    def toZio: Task[A] = Task.fromApiFuture(lfUio)
  }

  implicit class TaskObjApiFutureOps(private val taskObj: Task.type) extends AnyVal {

    def fromApiFuture[A](make: ExecutionContext => ApiFuture[A]): Task[A] =
      googleapis.fromApiFuture(make)

    def fromApiFuture[A](lfUio: UIO[ApiFuture[A]]): Task[A] =
      googleapis.fromApiFuture(lfUio)

  }

  implicit class ZioObjListenableFutureOps(private val zioObj: ZIO.type) extends AnyVal {

    def fromApiFuture[A](make: ExecutionContext => ApiFuture[A]): Task[A] =
      googleapis.fromApiFuture(make)

    def fromApiFuture[A](lfUio: UIO[ApiFuture[A]]): Task[A] =
      googleapis.fromApiFuture(lfUio)

  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {

    def fromApiFuture[A](thunk: => ApiFuture[A]): Fiber[Throwable, A] = {

      lazy val lf = thunk

      new Fiber[Throwable, A] {

        override def await: UIO[Exit[Throwable, A]] = Task.fromApiFuture(UIO.effectTotal(lf)).run

        override def poll: UIO[Option[Exit[Throwable, A]]] =
          UIO.effectSuspendTotal {
            if (lf.isDone)
              Task
                .effectSuspendWith(p => unwrapDone(p.fatal)(lf))
                .fold(Exit.fail, Exit.succeed)
                .map(Some(_))
            else
              UIO.succeed(None)
          }

        override def interrupt: UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

        override def inheritFiberRefs: UIO[Unit] = UIO.unit
      }
    }

  }

  implicit class TaskApiFutureOps[A](private val io: Task[A]) extends AnyVal {
    def toApiFuture: UIO[ApiFuture[A]] =
      io.fold(ApiFutures.immediateFailedFuture[A], ApiFutures.immediateFuture[A])
  }

  implicit class IOApiFutureOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toApiFutureWith(f: E => Throwable): UIO[ApiFuture[A]] =
      io.mapError(f).toApiFuture
  }

}
