---
layout: default
title: Actors
topic: Overview
---
<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

# Coral Actors

## Coral actor types
The following Coral Actors for stream transformations are defined:

name         | class | description
:----------- | :---- | :----------
`cassandra`  | [CassandraActor](https://github.com/coral-streaming/coral/wiki/CassandraActor) | connect to a Cassandra datasource
`fsm`        | [FsmActor](https://github.com/coral-streaming/coral/wiki/FsmActor) | select a state according to key
`generator` | [GeneratorActor](https://github.com/coral-streaming/coral/wiki/GeneratorActor) | generate data based on a JSON template and distribution definitions
`group`      | [GroupByActor](https://github.com/coral-streaming/coral/wiki/GroupByActor) | partition the stream
`httpbroadcast` | [HttpBroadcastActor](https://github.com/coral-streaming/coral/wiki/HttpBroadcastActor) | pass (HTTP supplied) JSON to other actors
`httpclient` | [HttpClientActor](https://github.com/coral-streaming/coral/wiki/HttpClientActor) | post to a service URL
`lookup`     | [LookupActor](https://github.com/coral-streaming/coral/wiki/LookupActor) | find data for a key value
`stats`      | [StatsActor](https://github.com/coral-streaming/coral/wiki/StatsActor) | accumulate some basic statistics
`threshold`  | [ThresholdActor](https://github.com/coral-streaming/coral/wiki/ThresholdActor) | emit only when a specified field value exceeds a threshold
`window`     | [WindowActor](https://github.com/coral-streaming/coral/wiki/WindowActor) | collect input objects and emit only when reaching a certain number or a certain time
`zscore`     | [ZscoreActor](https://github.com/coral-streaming/coral/wiki/ZscoreActor) | determine if a value is an outlier according to the Z-score statistic

## Creating a Coral actor
The JSON to create a Coral Actor conforms to [JSON API](http://jsonapi.org/). The attributes in the JSON to create a Coral Actor contain the following fields:

field     | type     | required | description
:-------- | :------- | :------- | :------------
`type`    | string   | yes | the name of the actor
`params`  | json     | yes | _depends on the actor_
`timeout` | json     | no | _timeout JSON_
`group`   | json     | no | _group by JSON_

The _timeout JSON_ is defined as follows:

field | type | required | description
:---- | :--- | :--- | :---------
`mode`     | string | yes | "continue" for continuing timer events; "exit" for act-once timer behavior
`duration` | string | yes | the duration of the timer in seconds

The timeout definition will determine how the timer function of an actor (if defined) is triggered.

The _group by JSON_ is defined with a single field:

field | type   | required | description
:---- | :----- | :------- | :---------
`by`  | string | yes | the value is the name of a field in the stream JSON

Having a group by field defined will invoke the (GroupByActor)[https://github.com/coral-streaming/coral/wiki/GroupByActor] to partition the stream and control the creation of underlying actors for each partition.

#### Example
Create the statistics actor with a timer action every 60 seconds.
Group the statistics by the value of the trigger-field `tag`.
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type":"stats",
      "timeout": {
        "duration": 60,
        "mode": "continue"
      },
      "params": {
        "field": "amount"
      },
      "group": {
        "by": "tag"
      }
    }
  }
}
{% endhighlight %}
Note: the `data`, `attributes` and `"type": "actors"` are used because of the conformation to [JSON API](http://jsonapi.org/).

## Trigger

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "id": "1",
    "attributes": {
      "input": {
        "trigger": {
          "in": {
            "type": "actor",
            "source": 2
          }
        }
      }
    }
  }
}
{% endhighlight %}

## Using your own Coral actors
When the provided Coral actors or a combination of them don't provide the functionality you need, you can write your own Coral actor and use it. To use your own Coral actor, you need to create a factory that extends the ActorPropFactory trait. See for an example the DefaultActorPropFactory, which contains all the provided Coral actors.

Next, you create a jar with your Coral actor(s) and your factory and add this jar to the classpath. To configure the Coral platform to use your factory, add the following to the application.conf:
{% highlight json %}
injections {
    actorPropFactories = ["mypackage.MyFactory"]
}
{% endhighlight %}
You can define more than one factory. When looking for a Coral actor by name, the search order is the order in which the factories are given. Before searching in user defined factories, the default factory with the provided Coral actors is searched for a given Coral actor name, so it is not possible to redefine one of the provided actors.