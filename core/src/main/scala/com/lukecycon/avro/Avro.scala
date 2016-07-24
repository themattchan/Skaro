package com.lukecycon.avro

import java.io.ByteArrayOutputStream
import org.apache.avro.io.EncoderFactory
import org.apache.avro.file.BZip2Codec
import java.nio.ByteBuffer
import org.apache.avro.io.DecoderFactory

object Avro {
  def schemaFor[T: AvroFormat] = implicitly[AvroFormat[T]].schema

  def write[T: AvroFormat](thing: T, compress: Boolean = false): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val encoder = EncoderFactory.get.binaryEncoder(out, null)

    implicitly[AvroFormat[T]].writeValue(thing, encoder)

    encoder.flush

    if (compress) {
      new BZip2Codec().compress(ByteBuffer.wrap(out.toByteArray)).array
    } else {
      out.toByteArray
    }
  }

  def writeHex[T: AvroFormat](thing: T): String =
    byteArrayToHexString(write(thing))

  def read[T: AvroFormat](bytes: Array[Byte],
                          compressed: Boolean = false): Either[String, T] = {
    val byts = if (compressed) {
      new BZip2Codec().decompress(ByteBuffer.wrap(bytes)).array
    } else {
      bytes
    }

    val decoder = DecoderFactory.get.binaryDecoder(byts, null)

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
