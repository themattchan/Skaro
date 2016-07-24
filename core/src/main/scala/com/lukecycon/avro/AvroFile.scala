package com.lukecycon.avro

import java.io.ByteArrayOutputStream
import org.apache.avro.io.EncoderFactory
import org.apache.avro.file.BZip2Codec
import java.nio.ByteBuffer
import org.apache.avro.io.DecoderFactory

object AvroFile {
  def dump[T: AvroFormat]
    (thing: T, compress: Boolean = false): Unit = {
    val schema = T.schema
    val encBytes = Avro.write(thing,compress)

  }

}
