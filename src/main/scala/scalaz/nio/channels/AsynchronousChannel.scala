package scalaz.nio.channels

import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousChannelGroup => JAsynchronousChannelGroup,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel,
  CompletionHandler => JCompletionHandler
}

import java.util.concurrent.TimeUnit


import scalaz._
import Scalaz._

import scalaz.nio.channels.AsynchronousChannel._
import scalaz.nio.{ Buffer, SocketAddress, SocketOption }
import scalaz.zio.Chunk
import scalaz.zio.{ Async, IO }
import scalaz.zio.duration._
import scalaz.zio.interop.scalaz72._

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final private[nio] def read(b: Buffer[Byte]): IO[Exception, Int] =
    wrap[Unit, JInteger](h => channel.read(b.buffer.asInstanceOf[JByteBuffer], (), h)).map(_.toInt)

  final def read(chunk: Chunk[Byte]): IO[Exception, Int] =
    for {
      b <- Buffer.byte(chunk)
      r <- read(b)
    } yield r

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final private[nio] def read[A](b: Buffer[Byte], attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.read(b.buffer.asInstanceOf[JByteBuffer], attachment, h))
      .map(_.toInt)

  final def read[A](chunk: Chunk[Byte], attachment: A): IO[Exception, Int] =
    for {
      b <- Buffer.byte(chunk)
      r <- read(b, attachment)
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final private[nio] def write(b: Buffer[Byte]): IO[Exception, Int] =
    wrap[Unit, JInteger](h => channel.write(b.buffer.asInstanceOf[JByteBuffer], (), h)).map(_.toInt)

  final def write(chunk: Chunk[Byte]): IO[Exception, Int] =
    for {
      b <- Buffer.byte(chunk)
      r <- write(b)
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final private[nio] def write[A](b: Buffer[Byte], attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.write(b.buffer.asInstanceOf[JByteBuffer], attachment, h))
      .map(_.toInt)

  final def write[A](chunk: Chunk[Byte], attachment: A): IO[Exception, Int] =
    for {
      b <- Buffer.byte(chunk)
      r <- write(b, attachment)
    } yield r

  /**
   * Closes this channel.
   */
  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

}

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections.
   */
  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address.jSocketAddress)).void

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  final def bind(address: SocketAddress, backlog: Int): IO[Exception, Unit] =
    IO.syncException(channel.bind(address.jSocketAddress, backlog)).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  /**
   * Accepts a connection.
   */
  final def accept: IO[Exception, AsynchronousSocketChannel] =
    wrap[Unit, JAsynchronousSocketChannel](h => channel.accept((), h))
      .map(AsynchronousSocketChannel(_))

  /**
   * Accepts a connection.
   */
  final def accept[A](attachment: A): IO[Exception, AsynchronousSocketChannel] =
    wrap[A, JAsynchronousSocketChannel](h => channel.accept(attachment, h))
      .map(AsynchronousSocketChannel(_))

  /**
   * The `SocketAddress` that the socket is bound to,
   * or the `SocketAddress` representing the loopback address if
   * denied by the security manager, or `Maybe.empty` if the
   * channel's socket is not bound.
   */
  final def localAddress: IO[Exception, Maybe[SocketAddress]] =
    IO.syncException(
      Maybe
        .fromNullable(channel.getLocalAddress)
        .map(new SocketAddress(_))
    )

  /**
   * Closes this channel.
   */
  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

}

object AsynchronousServerSocketChannel {

  def apply(): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(JAsynchronousServerSocketChannel.open())
      .map(new AsynchronousServerSocketChannel(_))

  def apply(
    channelGroup: AsynchronousChannelGroup
  ): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(
        JAsynchronousServerSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousServerSocketChannel(_))
}

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address.jSocketAddress)).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  final def shutdownInput: IO[Exception, Unit] =
    IO.syncException(channel.shutdownInput()).void

  final def shutdownOutput: IO[Exception, Unit] =
    IO.syncException(channel.shutdownOutput()).void

  final def remoteAddress: IO[Exception, Maybe[SocketAddress]] =
    IO.syncException(
      Maybe
        .fromNullable(channel.getRemoteAddress)
        .map(new SocketAddress(_))
    )

  final def localAddress: IO[Exception, Maybe[SocketAddress]] =
    IO.syncException(
      Maybe
        .fromNullable(channel.getLocalAddress)
        .map(new SocketAddress(_))
    )

  final def connect(socketAddress: SocketAddress): IO[Exception, Unit] =
    wrap[Unit, JVoid](h => channel.connect(socketAddress.jSocketAddress, (), h)).void

  final def connect[A](socketAddress: SocketAddress, attachment: A): IO[Exception, Unit] =
    wrap[A, JVoid](h => channel.connect(socketAddress.jSocketAddress, attachment, h)).void

  def read[A](dst: Chunk[Byte], timeout: Duration, attachment: A): IO[Exception, Int] = {
    def read[A](dst: Buffer[Byte], timeout: Duration, attachment: A): IO[Exception, Int] =
      wrap[A, JInteger] { h =>
        channel.read(
          dst.buffer.asInstanceOf[JByteBuffer],
          timeout.fold(Long.MaxValue, _.nanos),
          TimeUnit.NANOSECONDS,
          attachment,
          h
        )
      }.map(_.toInt)

    for {
      b <- Buffer.byte(dst)
      r <- read(b, timeout, attachment)
    } yield r
  }

  def read[A](
    dsts: IList[Chunk[Byte]],
    offset: Int,
    length: Int,
    timeout: Duration,
    attachment: A
  ): IO[Exception, Long] = {
    def read[A](
      dsts: IList[Buffer[Byte]],
      offset: Int,
      length: Int,
      timeout: Duration,
      attachment: A
    ): IO[Exception, Long] =
      wrap[A, JLong](
        h =>
        channel.read(
          dsts.map(_.buffer.asInstanceOf[JByteBuffer]).toList.toArray,
          offset,
          length,
          timeout.fold(Long.MaxValue, _.nanos),
          TimeUnit.NANOSECONDS,
          attachment,
          h
        )
      ).map(_.toLong)

    for {
      bs <- dsts.map(Buffer.byte(_)).sequence
      r <- read(bs, offset, length, timeout, attachment)
    } yield r
  }
}

object AsynchronousSocketChannel {

  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(JAsynchronousSocketChannel.open())
      .map(new AsynchronousSocketChannel(_))

  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(
        JAsynchronousSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)
}

class AsynchronousChannelGroup(val jChannelGroup: JAsynchronousChannelGroup) {}

object AsynchronousChannelGroup {

  def apply(): IO[Exception, AsynchronousChannelGroup] =
    ??? // IO.syncException { throw new Exception() }
}

object AsynchronousChannel {
  private[nio] def wrap[A, T](op: JCompletionHandler[T, A] => Unit): IO[Exception, T] =
    IO.async0[Exception, T] { k =>
      val handler = new JCompletionHandler[T, A] {
        def completed(result: T, u: A): Unit =
          k(IO.succeedLazy(result))

        def failed(t: Throwable, u: A): Unit =
          t match {
            case e: Exception => k(IO.fail(e))
            case _            => k(IO.die(t))
          }
      }

      try {
        op(handler)
        Async.later
      } catch {
        case e: Exception => Async.now(IO.fail(e))
        case t: Throwable => Async.now(IO.die(t))
      }
    }
}
