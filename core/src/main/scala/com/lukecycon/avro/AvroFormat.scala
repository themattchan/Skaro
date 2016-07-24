package com.lukecycon.avro

import org.apache.avro.Schema
import java.nio.ByteBuffer
import java.io.IOException
import org.apache.avro.io.BinaryEncoder
import org.apache.avro.io.BinaryDecoder
import scala.collection.generic
import scala.language.higherKinds

trait AvroFormat[T] {
  val schema: Schema

  def writeValue(v: T, to: BinaryEncoder): Unit

  def decodeValue(position: List[String],
                  from: BinaryDecoder): Either[String, T]
}

object AvroFormat extends AvroLowPriorityImplicits {

  def plstry[T](m: String)(t: => T) =
    try {
      Right(t)
    } catch {
      case _: IOException => Left(m)
    }

  abstract class PrimitiveAvroFormat[T](typ: Schema.Type,
                                        f: BinaryEncoder => T => Unit,
                                        d: BinaryDecoder => T)
      extends AvroFormat[T] {
    val schema = Schema.create(typ)

    def writeValue(v: T, to: BinaryEncoder) = f(to)(v)

    def decodeValue(position: List[String], from: BinaryDecoder) =
      try {
        Right(d(from))
      } catch {
        case _: IOException =>
          Left(s"At ${mkPath(position)}, expected ${typ.getName}")
      }
  }

  implicit object NullFormat
      extends PrimitiveAvroFormat[Null](Schema.Type.NULL,
                                        w => _ => w.writeNull, { r =>
                                          r.readNull; null
                                        })
  implicit object BooleanFormat
      extends PrimitiveAvroFormat[Boolean](Schema.Type.BOOLEAN,
                                           _.writeBoolean _,
                                           _.readBoolean)
  implicit object IntFormat
      extends PrimitiveAvroFormat[Int](Schema.Type.INT,
                                       _.writeInt _,
                                       _.readInt)
  implicit object LongFormat
      extends PrimitiveAvroFormat[Long](Schema.Type.LONG,
                                        _.writeLong _,
                                        _.readLong)
  implicit object FloatFormat
      extends PrimitiveAvroFormat[Float](Schema.Type.FLOAT,
                                         _.writeFloat _,
                                         _.readFloat)
  implicit object DoubltFormat
      extends PrimitiveAvroFormat[Double](Schema.Type.DOUBLE,
                                          _.writeDouble _,
                                          _.readDouble)
  implicit object BytesFormat
      extends PrimitiveAvroFormat[Seq[Byte]](Schema.Type.BYTES, { w => v =>
        w.writeBytes(ByteBuffer.wrap(v.toArray))
      }, _.readBytes(null).array.toSeq)
  implicit object StringFormat
      extends PrimitiveAvroFormat[String](Schema.Type.STRING,
                                          _.writeString _,
                                          _.readString)

  // Random instances
  implicit def optionAvroFormat[T: AvroFormat]: AvroFormat[Option[T]] =
    new AvroFormat[Option[T]] {
      val schema =
        Schema.createUnion(NullFormat.schema, implicitly[AvroFormat[T]].schema)

      def writeValue(v: Option[T], to: BinaryEncoder) {
        v match {
          case None =>
            to.writeLong(0)
            to.writeNull
          case Some(value) =>
            to.writeLong(1)
            implicitly[AvroFormat[T]].writeValue(value, to)
        }
      }

      def decodeValue(position: List[String], from: BinaryDecoder) = {
        val pos = mkPath(position :+ "Option[_]")
        val m = s"While reading $pos, "
        plstry(s"$m required long value")(from.readLong).right.flatMap { idx =>
          idx match {
            case 0 =>
              from.readNull
              Right(None)
            case 1 =>
              implicitly[AvroFormat[T]]
                .decodeValue(position :+ "Some[_]", from)
                .right
                .map(Some(_))
            case _ =>
              Left(s"$m unexpected index $idx")
          }
        }
      }
    }

  implicit def eitherAvroFormat[A: AvroFormat, B: AvroFormat]
    : AvroFormat[Either[A, B]] =
    new AvroFormat[Either[A, B]] {
      val schema = Schema.createUnion(implicitly[AvroFormat[A]].schema,
                                      implicitly[AvroFormat[B]].schema)

      def writeValue(v: Either[A, B], to: BinaryEncoder) {
        v match {
          case Left(value) =>
            to.writeLong(0)
            implicitly[AvroFormat[A]].writeValue(value, to)
          case Right(value) =>
            to.writeLong(1)
            implicitly[AvroFormat[B]].writeValue(value, to)
        }
      }

      def decodeValue(position: List[String], from: BinaryDecoder) = {
        val pos = mkPath(position :+ "Either[_, _]")
        val m = s"While reading $pos, "
        plstry(s"$m required long value")(from.readLong).right.flatMap { idx =>
          idx match {
            case 0 =>
              implicitly[AvroFormat[A]]
                .decodeValue(position :+ "Left[_]", from)
                .right
                .map(Left(_))
            case 1 =>
              implicitly[AvroFormat[B]]
                .decodeValue(position :+ "Right[_]", from)
                .right
                .map(Right(_))
            case _ =>
              Left(s"$m unexpected index $idx")
          }
        }
      }
    }
}

trait AvroLowPriorityImplicits {
  def mkPath(pths: List[String]) = "/" + pths.mkString("/")

  // Array like instances
  implicit def sequenceAvroFormat[T: AvroFormat, C[_] <: Iterable[T]](
      implicit bf: generic.CanBuild[T, C[T]],
      ev: AvroFormat[T]): AvroFormat[C[T]] =
    new AvroFormat[C[T]] {
      val schema = Schema.createArray(ev.schema)

      def writeValue(v: C[T], to: BinaryEncoder) {
        val things = v.toSeq

        to.writeArrayStart
        to.setItemCount(things.length)

        for (item <- things) {
          to.startItem
          ev.writeValue(item, to)
        }

        to.writeArrayEnd
      }

      def decodeValue(position: List[String], from: BinaryDecoder) =
        try {
          var i = from.readArrayStart
          var n = 0L
          val builder = bf()

          while (i != 0L) {
            for (_ <- 1L to i) {
              ev.decodeValue(position :+ s"[$n]", from)
                .fold(m => throw new Exception(m), builder += _)
              n = n + 1L
            }

            i = from.arrayNext
          }

          Right(builder.result)
        } catch {
          case _: IOException =>
            Left(s"While reading ${mkPath(position)}, unknown error")
          case e: Exception =>
            Left(e.getMessage)
        }
    }

  // Map instances
  implicit def mapAvroFormat[C[String, _] <: Iterable[(String, T)], T](
      implicit bf: generic.CanBuild[(String, T), C[String, T]],
      ev: AvroFormat[T]): AvroFormat[C[String, T]] =
    new AvroFormat[C[String, T]] {
      val schema = Schema.createMap(ev.schema)

      def writeValue(v: C[String, T], to: BinaryEncoder) {
        val things = v.toSeq

        to.writeMapStart
        to.setItemCount(things.length)

        for ((key, value) <- things) {
          to.startItem
          to.writeString(key)
          ev.writeValue(value, to)
        }

        to.writeMapEnd
      }

      def decodeValue(position: List[String], from: BinaryDecoder) =
        try {
          var i = from.readMapStart
          var n = 0L
          val builder = bf()

          while (i != 0L) {
            for (_ <- 1L to i) {
              val key = from.readString
              ev.decodeValue(position :+ s"[$key]", from)
                .fold(m => throw new Exception(m), v => builder += (key -> v))
              n = n + 1L
            }

            i = from.arrayNext
          }

          Right(builder.result)
        } catch {
          case _: IOException =>
            Left(s"While reading ${mkPath(position)}, unknown error")
          case e: Exception =>
            Left(e.getMessage)
        }
    }
}
