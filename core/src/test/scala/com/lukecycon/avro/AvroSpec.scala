package com.lukecycon.avro

import org.scalatest.FreeSpec
import org.scalatest.prop.Checkers
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._

class AvroSpec extends FreeSpec with Checkers {

  def roundTrip[T : AvroFormat](v: T) = Avro.read(Avro.write(v))

  def checkPrimative[T : Arbitrary : AvroFormat](name: String) =
    s"Avro roundTrip should be identity for $name" in {
      check { i: T =>
        roundTrip(i) == Right(i)
      }
    }

  checkPrimative[Boolean]("Boolean")
  checkPrimative[Int]("Int")
  checkPrimative[Long]("Long")
  checkPrimative[Float]("Float")
  checkPrimative[Double]("Double")
  checkPrimative[Seq[Byte]]("bytes")
  checkPrimative[String]("Sstring")
  checkPrimative[Option[Int]]("Option[_]")
  checkPrimative[Either[Int, String]]("Either[_]")
  checkPrimative[List[Int]]("List[_]")
  checkPrimative[Map[String, Int]]("Map[String, _]")
}
