/*
 * Copyright 2017 Fs2 Rabbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gvolpe.fs2rabbit.interpreter

import java.util.concurrent.Executors

import cats.effect.{Effect, IO}
import com.github.gvolpe.fs2rabbit.algebra.AMQPClient
import com.github.gvolpe.fs2rabbit.config.QueueConfig
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.typeclasses.StreamEval
import com.rabbitmq.client._
import fs2.async.mutable
import fs2.Stream

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class AmqpClientStream[F[_]](internalQ: mutable.Queue[IO, Either[Throwable, AmqpEnvelope]])(implicit F: Effect[F],
                                                                                            SE: StreamEval[F])
    extends AMQPClient[Stream[F, ?]] {

  private def defaultConsumer(channel: Channel): Consumer = {
    implicit val queueEC: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

    new DefaultConsumer(channel) {

      override def handleCancel(consumerTag: String): Unit =
        internalQ.enqueue1(Left(new Exception(s"Queue might have been DELETED! $consumerTag"))).unsafeRunSync()

      override def handleDelivery(consumerTag: String,
                                  envelope: Envelope,
                                  properties: AMQP.BasicProperties,
                                  body: Array[Byte]): Unit = {
        val msg   = new String(body, "UTF-8")
        val tag   = envelope.getDeliveryTag
        val props = AmqpProperties.from(properties)
        internalQ.enqueue1(Right(AmqpEnvelope(DeliveryTag(tag), msg, props))).unsafeRunSync()
      }

    }
  }

  override def basicAck(channel: Channel, tag: DeliveryTag, multiple: Boolean): Stream[F, Unit] = SE.evalF {
    channel.basicAck(tag.value, multiple)
  }

  override def basicNack(channel: Channel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): Stream[F, Unit] =
    SE.evalF {
      channel.basicNack(tag.value, multiple, requeue)
    }

  override def basicQos(channel: Channel, basicQos: BasicQos): Stream[F, Unit] = SE.evalF {
    channel.basicQos(basicQos.prefetchSize, basicQos.prefetchCount, basicQos.global)
  }

  override def basicConsume(channel: Channel,
                            queueName: QueueName,
                            autoAck: Boolean,
                            consumerTag: String,
                            noLocal: Boolean,
                            exclusive: Boolean,
                            args: Map[String, AnyRef]): Stream[F, String] = {
    val dc = defaultConsumer(channel)
    SE.evalF(channel.basicConsume(queueName.value, autoAck, consumerTag, noLocal, exclusive, args.asJava, dc))
  }

  override def basicPublish(channel: Channel,
                            exchangeName: ExchangeName,
                            routingKey: RoutingKey,
                            msg: AmqpMessage[String]): Stream[F, Unit] = SE.evalF {
    channel.basicPublish(exchangeName.value,
                         routingKey.value,
                         msg.properties.asBasicProps,
                         msg.payload.getBytes("UTF-8"))
  }

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey): Stream[F, Unit] = SE.evalF {
    channel.queueBind(queueName.value, exchangeName.value, routingKey.value)
  }

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey,
                         args: QueueBindingArgs): Stream[F, Unit] = SE.evalF {
    channel.queueBind(queueName.value, exchangeName.value, routingKey.value, args.value.asJava)
  }

  override def bindQueueNoWait(channel: Channel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey,
                               args: QueueBindingArgs): Stream[F, Unit] = SE.evalF {
    channel.queueBindNoWait(queueName.value, exchangeName.value, routingKey.value, args.value.asJava)
  }

  override def unbindQueue(channel: Channel,
                           queueName: QueueName,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey): Stream[F, Unit] = SE.evalF {
    channel.queueUnbind(queueName.value, exchangeName.value, routingKey.value)
  }

  override def bindExchange(channel: Channel,
                            destination: ExchangeName,
                            source: ExchangeName,
                            routingKey: RoutingKey,
                            args: ExchangeBindingArgs): Stream[F, Unit] = SE.evalF {
    channel.exchangeBind(destination.value, source.value, routingKey.value, args.value.asJava)
  }

  override def declareExchange(channel: Channel,
                               exchangeName: ExchangeName,
                               exchangeType: ExchangeType): Stream[F, Unit] = SE.evalF {
    channel.exchangeDeclare(exchangeName.value, exchangeType.toString.toLowerCase)
  }

  override def declareQueue(channel: Channel, queueConfig: QueueConfig): Stream[F, Unit] = SE.evalF {
    channel.queueDeclare(
      queueConfig.queueName.value,
      queueConfig.durable.asBoolean,
      queueConfig.exclusive.asBoolean,
      queueConfig.autoDelete.asBoolean,
      queueConfig.arguments.asJava
    )
  }

  override def declareQueueNoWait(channel: Channel, queueConfig: QueueConfig): Stream[F, Unit] =
    SE.evalF {
      channel.queueDeclareNoWait(
        queueConfig.queueName.value,
        queueConfig.durable.asBoolean,
        queueConfig.exclusive.asBoolean,
        queueConfig.autoDelete.asBoolean,
        queueConfig.arguments.asJava
      )
    }

  override def declareQueuePassive(channel: Channel, queueName: QueueName): Stream[F, Unit] = SE.evalF {
    channel.queueDeclarePassive(queueName.value)
  }

  override def deleteQueue(channel: Channel,
                           queueName: QueueName,
                           ifUnused: Boolean,
                           ifEmpty: Boolean): Stream[F, Unit] = SE.evalF {
    channel.queueDelete(queueName.value, ifUnused, ifEmpty)
  }

  override def deleteQueueNoWait(channel: Channel,
                                 queueName: QueueName,
                                 ifUnused: Boolean,
                                 ifEmpty: Boolean): Stream[F, Unit] = SE.evalF {
    channel.queueDeleteNoWait(queueName.value, ifUnused, ifEmpty)
  }

}
