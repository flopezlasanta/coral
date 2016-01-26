/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.actors.connector

import java.util.Properties
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import io.coral.actors.CoralActor.RegisterActor
import io.coral.actors.connector.KafkaConsumerActor.ReadMessageQueue
import io.coral.lib.{JsonDecoder, KafkaJsonConsumer, KafkaJsonStream}
import org.json4s.JsonAST.{JNothing, JNull, JValue}
import org.json4s.jackson.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._

object KafkaConsumerActorSpec {
	class TestKafkaJsonConsumer(stream: KafkaJsonStream)
		extends KafkaJsonConsumer(JsonDecoder) {
		override def stream(topic: String, properties: Properties): KafkaJsonStream = stream
	}

	class TestKafkaJsonStream extends KafkaJsonStream(null, null) {
		private var message: List[JValue] = Nil

		def publish(value: Any*): Unit = {
			message = value.toList.map(_ match {
				case j: JValue => j
				case _ => JNothing
			})
		}

		def json(message: String): JValue = try {
			parse( s"""{ "message": "$message" }""")
		} catch {
			case e: Throwable => JNothing
		}

		@inline
		override def hasNextInTime: Boolean = message match {
			case Nil =>
				Thread.sleep(100)
				false
			case m :: ms =>
				true
		}

		@inline
		override def next: JValue = message match {
			case Nil => JNull
			case m :: ms =>
				message = ms
				m
		}

		@inline
		override def commitOffsets: Unit = {
		}
	}
}

class KafkaConsumerActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with ScalaFutures {
	import KafkaConsumerActorSpec._
	def this() = this(ActorSystem("KafkaConsumerActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
		Await.result(system.terminate(), Timeout(2.seconds).duration)
	}

	"A KafkaConsumerActor" should {
		"provide actor properties" in {
			val json = parse("""{
				  | "type": "kafka-consumer",
				  |  "params": {
				  |    "kafka" : {
				  |      "group.id": "xyz",
				  |      "zookeeper.connect": "localhost:2181"
				  |    },
				  |    "topic": "abc"
				  | } }""".stripMargin)
			val props = KafkaConsumerActor(json)
			props.get.actorClass should be(classOf[KafkaConsumerActor])
			val props2 = KafkaConsumerActor(json, JsonDecoder)
			props2.get.actorClass should be(classOf[KafkaConsumerActor])
		}

		"read kafka parameters" in {
			val json = parse("""{
				  | "type": "kafka-consumer",
				  |  "params": {
				  |    "kafka" : {
				  |      "group.id": "xyz",
				  |      "zookeeper.connect": "localhost:2181"
				  |    },
				  |    "topic": "abc"
				  | } }""".stripMargin)
			val (properties, topic) = KafkaConsumerActor.getParams(json).get
			topic shouldBe "abc"
			properties.size shouldBe 4
			properties.getProperty("group.id") shouldBe "xyz"
			properties.getProperty("zookeeper.connect") shouldBe "localhost:2181"
			properties.getProperty("consumer.timeout.ms") shouldBe "500"
			properties.getProperty("auto.commit.enable") shouldBe "false"
		}

		"emit on receiving a message" in {
			val json = parse("""{
				  | "type": "kafka-consumer",
				  |  "params": {
				  |    "kafka" : {
				  |      "group.id": "xyz",
				  |      "zookeeper.connect": "localhost:2181"
				  |    },
				  |    "topic": "abc"
				  | } }""".stripMargin)
			val stream = new TestKafkaJsonStream
			val kafka = new TestKafkaJsonConsumer(stream)
			val props = Props(classOf[KafkaConsumerActor], json, kafka)
			val actorRef = system.actorOf(props) // using TestActorRef blocks test thread
			actorRef ! ReadMessageQueue()
			Thread.sleep(1000)
			val probe = TestProbe()
			actorRef ! RegisterActor(probe.ref)
			stream.publish(stream.json("bla"), stream.json("blabla"), stream.json("blablabla"))

			probe.expectMsg(100.millis, stream.json("bla"))
			probe.expectMsg(100.millis, stream.json("blabla"))
			probe.expectMsg(100.millis, stream.json("blablabla"))
			probe.expectNoMsg(100.millis)
		}

		"emit nothing when there are no messages message" in {
			val json = parse("""{
				  | "type": "kafka-consumer",
				  |  "params": {
				  |    "kafka" : {
				  |      "group.id": "xyz",
				  |      "zookeeper.connect": "localhost:2181"
				  |    },
				  |    "topic": "abc"
				  | } }""".stripMargin)
			val stream = new TestKafkaJsonStream
			val kafka = new TestKafkaJsonConsumer(stream)
			val props = Props(classOf[KafkaConsumerActor], json, kafka)
			val actorRef = system.actorOf(props)
			val probe = TestProbe()
			actorRef ! RegisterActor(probe.ref)
			probe.expectNoMsg(500.millis)
		}

		"emit nothing when the Kafka message is not valid Json" in {
			val json = parse("""{
				  | "type": "kafka-consumer",
				  |  "params": {
				  |    "kafka" : {
				  |      "group.id": "xyz",
				  |      "zookeeper.connect": "localhost:2181"
				  |    },
				  |    "topic": "abc"
				  | } }""".stripMargin)
			val stream = new TestKafkaJsonStream
			val kafka = new TestKafkaJsonConsumer(stream)
			val props = Props(classOf[KafkaConsumerActor], json, kafka)
			val actorRef = system.actorOf(props) // using TestActorRef blocks test thread
			val probe = TestProbe()
			actorRef ! RegisterActor(probe.ref)
			stream.publish("oops")
			probe.expectNoMsg(500.millis)
		}
	}
}