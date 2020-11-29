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

trait ResourceManager[F[_], K, V] {

  /** Submit a resource to be managed, making the allocated resource
    * available at `key`.
    *
    * If a resource already exists for `key`, it is released and replaced by
    * the new resource.
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

  /** Retrieve the resource at `key`, `None` indicates `key` is unknown.
    *
    * Semantically blocks until resource acquisition has terminated.
    */
  def get(key: K): F[Option[V]]

  /** Alias for `get`. */
  def apply(key: K): F[Option[V]] =
    get(key)

  /** Retrieve the resource at `key`, returning `None` if `key` is unknown
    * or the outcome of resource acquisition otherwise.
    *
    * Semantically blocks until resource acquisition has terminated.
    */
  def join(key: K): F[Option[Outcome[F, Throwable, V]]]
}

object ResourceManager {
  /** A `ResourceManager` that eagerly allocates resources on calls to `manage`.
    *
    * If resource acquisition fails no entry will exist in the manager for the
    * failed key and the outcome will be reported to any awaiting `join`s.
    */
  def eager[F[_], K, V]: Resource[F, ResourceManager[F, K, V]] = ???

  /** A `ResourceManager` that allocates resources on first access.
    *
    * If resource acquisition fails, it is reported in results of `get` and
    * `join`. Subsequent access of a resource that failed to acquire will
    * reattempt acquisition.
    */
  def lazy[F[_], K, V]: Resource[F, ResourceManager[F, K, V]] = ???

  private final class Eager[F[_], K, V](
      managed: Ref[F, Map[K, EagerResource[F, V]]])(
      implicit F: MonadCancel[F, Throwable])
      extends ResourceManager[F, K, V] {

    def manage(key: K, resource: Resource[F, V]): F[Unit] = {
      val id = new Token

      val allocate =
        F.guaranteeCase(resource.allocated) {
          case Outcome.Succeeded(fa) =>
            F.flatMap(fa) {
              case (v, rel) =>
                managed.modify { m =>
                  m.get(key) match {
                    case Some(Acquiring(`id`, oc
                }

          case oc =>
            managed.modify { m =>
              m.get(key).fold((m, F.unit)) {
                case Some(Acquiring(`id`, oc, _)) =>
              }
            }

          case Outcome.Canceled() =>

          case (v, rel) =>
            managed.modify { m =>
              m.get(key) match {
                case Some(Acquiring(`id`, oc, _)) =>
              }
            }
        } {
          case ((_, _), Outcome.Succeeded(_)) => F.unit
          case ((_, rel), _) => rel
        }

      val updated =
        F.deferred[Outcome[F, Throwable, V]].flatMap { d =>

            F.flatMap(resource.allocated) {
              case (v, rel) =>

            }

          managed.modify { m =>
            m.get(key) match {
              case None =>
                (m.updated(key, Acquiring(
            }
          }
        }

      F.uncancelable(_ => F.flatten(updated))
    }

    // TODO: should we tombstone here instead, to ensure there is only one
    //       resource acquision happening per-key?
    def release(key: K): F[Unit] = {
      val updated = managed.modify { m =>
        m.get(key).fold((m, F.unit)) {
          case Acquired(_, r) =>
            (m - key, F.void(F.attempt(r)))

          case Acquiring(_, oc, c) =>
            (m - key, F.void(F.attempt(c)) *> oc.complete(Outcome.canceled))
        }
      }

      F.uncancelable(_ => F.flatten(updated))
    }

    def get(key: K): F[Option[V]] =
      F.flatMap(join(key))(_.flatTraverse {
        case Outcome.Succeeded(v) => F.map(v)(Some(_))
        case _ => F.pure(None)
      })

    def join(key: K): F[Option[Outcome[F, Throwable, V]]] =
      F.flatMap(managed.get(key))(_.traverse {
        case Acquired(v, _) => F.pure(Outcome.succeeded(F.pure(v)))
        case Acquiring(_, oc, _) => oc.get
      })
  }

  private final class Token()

  private sealed trait ManagedResource[F[_], V] extends Product with Serializable
  private final case class Unacquired[F[_], V](resource: Resource[F, V]) extends ManagedResource[F, V]
  private sealed trait EagerResource[F[_], V] extends ManagedResource[F, V]
  private final case class Acquired[F[_], V](value: V, release: F[Unit]) extends EagerResource[F, V]
  private final case class Acquiring[F[_], V](id: Token, outcome: Deferred[F, Outcome[F, Throwable, V]], cancel: F[Unit]) extends EagerResource[F, V]
}
