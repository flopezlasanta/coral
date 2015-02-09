package io.coral.actors

import io.coral.actors.transform._
import org.json4s._
import org.json4s.jackson.JsonMethods._

object CoralActorFactory {
	def getProps(json: JValue) = {
		implicit val formats = org.json4s.DefaultFormats

		println(pretty(json))

		// check for grouping, if so generate a group actor and move on ...
		// otherwise, generate the proper actor
		val groupByProps = (json \ "group" \ "by").extractOpt[String] match {
			case Some(x) => GroupByActor(json)
			case None => None
		}

		println(groupByProps.toString)

		val actorProps = for {
			actorType <- (json \ "type").extractOpt[String]

			props <- actorType match {
				case "zscore" => ZscoreActor(json)
				case "histogram" => HistogramActor(json)
				case "rest" => RestActor(json)
				case "httpclient" => HttpClientActor(json)
			}
		} yield props

		groupByProps orElse actorProps
	}
}