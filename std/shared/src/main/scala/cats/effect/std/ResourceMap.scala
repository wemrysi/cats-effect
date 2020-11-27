/*
 * Copyright 2020 Typelevel
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

package cats.effect.std

import cats.effect.kernel.{Outcome, Resource}

trait ResourceMap[F[_], K, V] {

  /** Submit a resource to be managed, when allocated the managed value will
    * be available at `key`.
    *
    * If a resource already exists for `key`, its finalizers will be run and
    * subsequent requests for `key` will return the new resource.
    *
    * If allocation of the resource fails, it will be retried on next access.
    */
  def manage(key: K, resource: Resource[F, V]): F[Unit]

  /** Alias for `manage`. */
  def +=(pair: (K, Resource[F, V])): F[Unit] =
    manage(pair._1, pair._2)

  /** Cease managing the resource at `key`, running any finalizers for acquired
    * resources and canceling any acquisition in progress.
    */
  def release(key: K): F[Unit]

  /** Alias for `release`. */
  def -=(key: K): F[Unit] =
    release(key)

  /** Retrieve the resource at `key` or the reason it failed to acquire. `None`
    * indicates `key` is unknown to the manager.
    *
    * Semantically blocks until resource acquisition has completed.
    */
  def apply(key: K): F[Option[Either[Throwable, V]]] =

  /** Alias for `apply`. */
  def get(key: K): F[Option[Either[Throwable, V]]] =
    apply(key)

  /** Retrieve the resource at `key`, returning `None` if `key` is unknown to
    * the manager.
    *
    * Semantically blocks until resource acquisition has completed.
    */
  def join(key: K): F[Option[V]]
}

object ResourceMap {
  /** A `ResourceMap` that eagerly allocates resources on calls to `manage`. */
  def eager[F[_], K, V]: Resource[F, ResourceMap[F, K, V]] =
    apply(false)

  /** A `ResourceMap` that allocates resources on first access. */
  def lazy[F[_], K, V]: Resource[F, ResourceMap[F, K, V]] =
    apply(true)

  private def apply[F[_], K, V](lazyAllocation: Boolean): Resource[F, ResourceMap[F, K, V]] = ???

  private final class Impl[F[_], K, V](lazyAllocation: Boolean) extends ResourceMap[F, K, V] {
    // registered - lazy allocation, 
    // acquiring
    //

    def join(key: K): F[Option[V]] =
      apply(key).map(_.sequence).rethrow
  }

  private sealed trait ManagedResource[F[_], V] extends Product with Serializable
  private final case class Acquired[F[_], V](resource: V, release: F[Unit]) extends ManagedResource[F, V]
  private final case class Acquiring[F[_], V](result: Deferred[F, Option[Either[Throwable, V]]], cancel: F[Unit]) extends ManagedResource[F, V]
  private final case class Unacquired[F[_], V](resource: Resource[F, V]) extends ManagedResource[F, V]
}
