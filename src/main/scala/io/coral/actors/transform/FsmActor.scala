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

package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import org.json4s._
import io.coral.actors.{NoEmitTrigger, CoralActor}

object FsmActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			key <- (json \ "params" \ "key").extractOpt[String]
			table <- (json \ "params" \ "table").extractOpt[Map[String, Map[String, String]]]
			s0 <- (json \ "params" \ "s0").extractOpt[String]
			if (table.contains(s0))
		} yield {
			(key, table, s0)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[FsmActor], json))
	}
}

class FsmActor(json: JObject)
	extends CoralActor(json)
	with ActorLogging
	with NoEmitTrigger {

	val (key, table, s0) = FsmActor.getParams(json).get
	// fsm state
	var s = s0
	override def state = Map(("s", JString(s)))

	override def noEmitTrigger(json: JObject) = {
		for {
			value <- (json \ key).extractOpt[String]
		} yield {
			// compute (local variables & update state)
			val e = table.getOrElse(s, table(s0))
			s = e.getOrElse(value, s)
		}
	}
}