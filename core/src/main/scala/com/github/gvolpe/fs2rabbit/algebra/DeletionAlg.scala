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

package com.github.gvolpe.fs2rabbit.algebra

import com.github.gvolpe.fs2rabbit.model.QueueName
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.Channel

trait DeletionAlg[F[_]] {

  def deleteQueue(channel: Channel,
                  queueName: QueueName,
                  ifUnused: Boolean = true,
                  ifEmpty: Boolean = true): F[Queue.DeleteOk]

  def deleteQueueNoWait(channel: Channel,
                        queueName: QueueName,
                        ifUnused: Boolean = true,
                        ifEmpty: Boolean = true): F[Unit]

}