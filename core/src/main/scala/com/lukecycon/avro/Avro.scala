package com.lukecycon.avro

import java.io.ByteArrayOutputStream
import org.apache.avro.io.EncoderFactory
import org.apache.avro.io.DecoderFactory

object Avro {
  def schemaFor[T: AvroFormat] = implicitly[AvroFormat[T]].schema

  def write[T: AvroFormat](thing: T): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val encoder = EncoderFactory.get.binaryEncoder(out, null)

    implicitly[AvroFormat[T]].writeValue(thing, encoder)

    encoder.flush

    out.toByteArray
  }

  def writeHex[T: AvroFormat](thing: T): String =
    byteArrayToHexString(write(thing))

  def read[T: AvroFormat](bytes: Array[Byte]): Either[String, T] = {
    val decoder = DecoderFactory.get.binaryDecoder(bytes, null)

    implicitly[AvroFormat[T]].decodeValue(Nil, decoder)
  }

  def readHex[T: AvroFormat](hex: String): Either[String, T] =
    read(
        hex
          .replace(" ", "")
          .grouped(2)
          .map(Integer.parseInt(_, 16).toByte)
          .toArray)

  private def byteArrayToHexString(bb: Array[Byte]): String =
    bb.map("%02X" format _).mkString.grouped(2).mkString(" ")
}
