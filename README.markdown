Skaro
======

A macro-based Avro library for Scala, build atop the official [Apache Avro](http://avro.apache.org/docs/current/) Java API.

```scala
scala> import com.lukecycon.avro._
import com.lukecycon.avro._

scala> val data = Map("foo" -> List(23, 45), "bar" -> List(2435, 65))
data: scala.collection.immutable.Map[String,List[Int]] = Map(foo -> List(23, 45), bar -> List(2435, 65))

scala> val encoded = Avro.write(data)
encoded: Array[Byte] = Array(4, 6, 102, 111, 111, 4, 46, 90, 0, 6, 98, 97, 114, 4, -122, 38, -126, 1, 0, 0)

scala> val decoded = Avro.read[Map[String, List[Int]]](encoded)
decoded: Either[String,Map[String,List[Int]]] = Right(Map(foo -> List(23, 45), bar -> List(2435, 65)))

scala> data == decoded.right.get
res1: Boolean = true
```

## What we try to accomplish

- [x] Provide compile time schema generation for types currently supported by the project
- [ ] Symmetric serialization/deserialization of
    - [x] Avro primitive types
    - [x] Arrays, mapping so conventient Scala types
    - [x] Maps, mapping to convenient Scala types
    - [x] Maps, mapping to convenient Scala types
    - [ ] Records, mapping to all types of Scala classes
    - [ ] Enums, mapping from both Java and Scala enums
- [x] Schema specializations for some Scala types, such as `Option` and `Either`
- [ ] Convenience methods for writing Avro Object Container Files
- [x] A non-standard data-only binary representation that optionally supports compression (that is, does not contain schema in serialized form)
    - [x] Non-compressed version
    - [x] Compressed version

## What we will not attempt to cover

- An RPC framework
- Schema resolution

If there is enough interest, and contributor time allows, these could later be included into the scope of the project. Similarly, popularly requested
features may be included as well.

## Installing

As the project is still in its infancy, there are no published artifacts. See `Building`.

## Schema-less Compressed Binary Representation

A BZip2 compressed, schema-less binary representation can be generated and parsed by Skaro. This is largely
motivated by often having to store large collections of very similar strings, efficiently.

This is best exemplified by this, real world, use case: I have a web application that needs to store collections of photos.

```scala
type PhotoID = String
type URL = String
type Photos = Map[PhotoID, URL]
```

As an example, let's generate some dummy data.

```scala
val FakeBaseURL = "https://photo.someawesome.site/user/123/"
val rand = new java.util.Random

scala> val data = (for(i <- 1 to 365) yield (s"photo$i", s"$FakeBaseURL${rand.nextInt}")).toMap
data: scala.collection.immutable.Map[String,String] = Map(photo271 -> https://photo.someawesome.site/user/123/1889734359,
                                                          photo228 -> https://photo.someawesome.site/user/123/1872501067,
                                                          photo159 -> https://photo.someawesome.site/user/123/-294314680,
                                                          photo286 -> https://photo.someawesome.site/user/123/1262966431,
                                                          photo38 -> https://photo.someawesome.site/user/123/607492334...
```

We can use Skaro's normal serialization method this data:

```scala
import com.lukecycon.avro._
val uncompressed = Avro.write(data)

scala> Avro.read[Photos](uncompressed).right.get == data
res1: Boolean = true
```

But we can also used the compression feature to serialize the same:

```scala
val compressed = Avro.write(data, compress = true)

scala> Avro.read[Photos](compressed, compressed = true).right.get == data
res2: Boolean = true
```

We can see from deserializing each payload that the results are the
same. When comparing their serialized sizes, however, we can see the
two methods yield very different results.

```scala
scala> uncompressed.length
res3: Int = 21809

scala> compressed.length
res4: Int = 2744

scala> (uncompressed.length - compressed.length) / uncompressed.length.toDouble
res5: Double = 0.8741803842450364
```

That's an 87% reduction in size!

Now imagine, for a moment, that each user of my application is
generating data such as this (or larger!) that needs to be
stored. Between saving the schema along with the data, and not
compressing the serialized format, my website is now wasting quite a
bit of space. With Skaro's compact format, I can trade some fragility
to schema updates for a significant space reduction.

## Building

Once you have cloned the repository, the following should suffice:

```
sbt build
```

## Testing

Tests are written using [ScalaTest](http://www.scalatest.org/) and [ScalaCheck](http://www.scalacheck.org/). When possible, property based tests are used
to prove the functional accuracy of the library.

Running the tests can be accomplished as follows:

```
sbt test
```

## Contributing

Bug reports and feature requests are always welcome, to be tracked on the project's GitHub repository issue tracker. As always, pull requests are highly encouraged :)

Before opening a pull request, please ensure that all tests pass and format your changed source code by running:

```
sbt scalafmt
```

## Authors

- Luke Cycon ([@lcycon](https://github.com/lcycon))

## License

Apache Avro is a trademark of The Apache Software Foundation.

Copyright (c) 2016, Luke Cycon

Skaro is distributed under the GNU GPL v3 License, the full text of which can be found in the `LICENSE` file.
