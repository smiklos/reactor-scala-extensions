package reactor.core.scala.publisher

import java.lang.{Long => JLong}
import java.util.function.{Function, Supplier}
import java.util.{List => JList}

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import reactor.core.publisher.FluxSink.OverflowStrategy
import reactor.core.publisher.{FluxSink, Flux => JFlux}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration.Duration


/**
  * A Reactive Streams [[Publisher]] with rx operators that emits 0 to N elements, and then completes
  * (successfully or with an error).
  *
  * <p>
  * <img src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/flux.png" alt="">
  * <p>
  *
  * <p>It is intended to be used in implementations and return types. Input parameters should keep using raw
  * [[Publisher]] as much as possible.
  *
  * <p>If it is known that the underlying [[Publisher]] will emit 0 or 1 element, [[Mono]] should be used
  * instead.
  *
  * <p>Note that using state in the lambdas used within Flux operators
  * should be avoided, as these may be shared between several [[Subscriber Subscribers]].
  *
  * @tparam T the element type of this Reactive Streams [[Publisher]]
  * @see [[Mono]]
  */
class Flux[T](private[publisher] val jFlux: JFlux[T]) extends Publisher[T] with MapablePublisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = jFlux.subscribe(s)

  /**
    *
    * Emit a single boolean true if all values of this sequence match
    * the given predicate.
    * <p>
    * The implementation uses short-circuit logic and completes with false if
    * the predicate doesn't match a value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/all.png" alt="">
    *
    * @param predicate the predicate to match all emitted items
    * @return a [[Mono]] of all evaluations
    */
  final def all(predicate: T => Boolean): Mono[Boolean] = Mono(jFlux.all(predicate)).map(Boolean2boolean)

  /**
    * Emit a single boolean true if any of the values of this [[Flux]] sequence match
    * the predicate.
    * <p>
    * The implementation uses short-circuit logic and completes with true if
    * the predicate matches a value.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/any.png" alt="">
    *
    * @param predicate predicate tested upon values
    * @return a new [[Flux]] with <code>true</code> if any value satisfies a predicate and <code>false</code>
    *         otherwise
    *
    */
  final def any(predicate: T => Boolean): Mono[Boolean] = Mono(jFlux.any(predicate)).map(Boolean2boolean)

  /**
    * Immediately apply the given transformation to this [[Flux]] in order to generate a target type.
    *
    * `flux.as(Mono::from).subscribe()`
    *
    * @param transformer the [[Function1]] to immediately map this [[Flux]]
    *                    into a target type
    *                    instance.
    * @tparam P the returned type
    * @return a an instance of P
    * @see [[Flux.compose]] for a bounded conversion to [[Publisher]]
    */
  final def as[P](transformer: Flux[T] => P): P = jFlux.as(transformer)

  /**
    * Intercepts the onSubscribe call and makes sure calls to Subscription methods
    * only happen after the child Subscriber has returned from its onSubscribe method.
    *
    * <p>This helps with child Subscribers that don't expect a recursive call from
    * onSubscribe into their onNext because, for example, they request immediately from
    * their onSubscribe but don't finish their preparation before that and onNext
    * runs into a half-prepared state. This can happen with non Reactor mentality based Subscribers.
    *
    * @return non reentrant onSubscribe [[Flux]]
    */
  //  TODO: How to test?
  final def awaitOnSubscribe() = Flux(jFlux.awaitOnSubscribe())

  /**
    * Blocks until the upstream signals its first value or completes.
    *
    * @return the [[Some]] value or [[None]]
    */
  final def blockFirst(): Option[T] = Option(jFlux.blockFirst())

  /**
    * Blocks until the upstream signals its first value or completes.
    *
    * @param d max duration timeout to wait for.
    * @return the [[Some]] value or [[None]]
    */
  final def blockFirst(d: Duration): Option[T] = Option(jFlux.blockFirst(d))

  /**
    * Blocks until the upstream signals its first value or completes.
    *
    * @param timeout max duration timeout in millis to wait for.
    * @return the [[Some]] value or [[None]]
    */
  final def blockFirstMillis(timeout: Long) = Option(jFlux.blockFirstMillis(timeout))

  /**
    * Blocks until the upstream completes and return the last emitted value.
    *
    * @return the last [[Some value]] or [[None]]
    */
  final def blockLast() = Option(jFlux.blockLast())

  /**
    * Blocks until the upstream completes and return the last emitted value.
    *
    * @param d max duration timeout to wait for.
    * @return the last [[Some value]] or [[None]]
    */
  final def blockLast(d: Duration) = Option(jFlux.blockLast(d))

  /**
    * Blocks until the upstream completes and return the last emitted value.
    *
    * @param timeout max duration timeout in millis to wait for.
    * @return the last [[Some value]] or [[None]]
    */
  final def blockLastMillis(timeout: Long) = Option(jFlux.blockLastMillis(timeout))

  /**
    * Collect incoming values into a [[Seq]] that will be pushed into the returned [[Flux]] on complete only.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffer.png"
    * alt="">
    *
    * @return a buffered { @link Flux} of at most one [[Seq]]
    * @see #collectList() for an alternative collecting algorithm returning [[Mono]]
    */
  final def buffer(): Flux[Seq[T]] = {
    Flux(jFlux.buffer()).map(_.asScala)
  }

  /**
    * Collect incoming values into multiple [[Seq]] buckets that will be pushed into the returned [[Flux]]
    * when the given max size is reached or onComplete is received.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    *
    * @param maxSize the maximum collected size
    * @return a microbatched [[Flux]] of [[Seq]]
    */
  final def buffer(maxSize: Int): Flux[Seq[T]] = Flux(jFlux.buffer(maxSize)).map(_.asScala)

  /**
    * Collect incoming values into multiple [[Seq]] buckets that will be
    * pushed into the returned [[Flux]]
    * when the given max size is reached or onComplete is received.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    *
    * @param maxSize        the maximum collected size
    * @param bufferSupplier the collection to use for each data segment
    * @tparam C the supplied [[Seq]] type
    * @return a microbatched [[Flux]] of [[Seq]]
    */
  final def buffer[C <: mutable.ListBuffer[T]](maxSize: Int, bufferSupplier: () => C): Flux[mutable.Seq[T]] = {
    Flux(jFlux.buffer(maxSize, new Supplier[JList[T]] {
      override def get(): JList[T] = bufferSupplier().asJava
    })).map(_.asScala)
  }

  /**
    * Collect incoming values into multiple [[Seq]] that will be pushed into the returned [[Flux]] when the
    * given max size is reached or onComplete is received. A new container [[Seq]] will be created every given
    * skip count.
    * <p>
    * When Skip > Max Size : dropping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersizeskip.png"
    * alt="">
    * <p>
    * When Skip < Max Size : overlapping buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersizeskipover.png"
    * alt="">
    * <p>
    * When Skip == Max Size : exact buffers
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/buffersize.png"
    * alt="">
    *
    * @param skip    the number of items to skip before creating a new bucket
    * @param maxSize the max collected size
    * @return a microbatched [[Flux]] of possibly overlapped or gapped [[Seq]]
    */
  final def buffer(maxSize: Int, skip: Int): Flux[Seq[T]] = Flux(jFlux.buffer(maxSize, skip)).map(_.asScala)

  /**
    * Defer the transformation of this [[Flux]] in order to generate a target [[Flux]] for each
    * new [[Subscriber]].
    *
    * `flux.compose(Mono::from).subscribe()`
    *
    * @param transformer the [[Function1]] to map this [[Flux]] into a target [[Publisher]]
    *                    instance for each new subscriber
    * @tparam V the item type in the returned [[Publisher]]
    * @return a new [[Flux]]
    * @see [[Flux.transform]] for immmediate transformation of [[Flux]]
    * @see [[Flux.as]] for a loose conversion to an arbitrary type
    */
  final def compose[V](transformer: Flux[T] => Publisher[V]) = Flux(jFlux.compose[V](transformer))

  /**
    * Transform this [[Flux]] in order to generate a target [[Flux]]. Unlike [[Flux.compose]], the
    * provided function is executed as part of assembly.
    * *
    *
    * @example {{{
    * val applySchedulers = flux => flux.subscribeOn(Schedulers.elastic()).publishOn(Schedulers.parallel());
    * flux.transform(applySchedulers).map(v => v * v).subscribe()
    *          }}}
    * @param transformer the [[Function1]] to immediately map this [[Flux]] into a target [[Flux]]
    *                    instance.
    * @tparam V the item type in the returned [[Flux]]
    * @return a new [[Flux]]
    * @see [[Flux.compose]] for deferred composition of [[Flux]] for each [[Subscriber]]
    * @see [[Flux.as]] for a loose conversion to an arbitrary type
    */
  final def transform[V](transformer: Flux[T] => Publisher[V]) = Flux(jFlux.transform[V](transformer))

  def count(): Mono[Long] = Mono[Long](jFlux.count().map(new Function[JLong, Long] {
    override def apply(t: JLong) = Long2long(t)
  }))

  def take(n: Long) = new Flux[T](jFlux.take(n))

  def sample(duration: Duration) = new Flux[T](jFlux.sample(duration))

  override def map[U](mapper: (T) => U) = new Flux[U](jFlux.map(mapper))

  /**
    * Provide a default unique value if this sequence is completed without any data
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defaultifempty.png" alt="">
    * <p>
    *
    * @param defaultV the alternate value if this sequence is empty
    * @return a new [[Flux]]
    */
  final def defaultIfEmpty(defaultV: T) = new Flux[T](jFlux.defaultIfEmpty(defaultV))

  /**
    * Attach a Long customer to this [[Flux]] that will observe any request to this [[Flux]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonrequest.png" alt="">
    *
    * @param consumer the consumer to invoke on each request
    * @return an observed  [[Flux]]
    */
  final def doOnRequest(consumer: Long => Unit): Flux[T] = Flux(jFlux.doOnRequest(consumer))

  /**
    * Triggered when the [[Flux]] is subscribed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/doonsubscribe.png" alt="">
    * <p>
    *
    * @param onSubscribe the callback to call on [[org.reactivestreams.Subscriber.onSubscribe]]
    * @return an observed  [[Flux]]
    */
  final def doOnSubscribe(onSubscribe: Subscription => Unit): Flux[T] = Flux(jFlux.doOnSubscribe(onSubscribe))

  final def asJava(): JFlux[T] = jFlux
}

object Flux {

  private def apply[T](jFlux: JFlux[T]): Flux[T] = new Flux[T](jFlux)

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The upstreams [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T type of the value from sources
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced combinations
    */
  def combineLatest[T, V](combinator: Array[AnyRef] => V, sources: Publisher[_ <: T]*): Flux[V] = Flux(JFlux.combineLatest(combinator, sources: _*))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The upstreams [[Publisher]] to subscribe to.
    * @param prefetch   demand produced to each combined source [[Publisher]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T type of the value from sources
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced combinations
    */
  def combineLatest[T, V](combinator: Array[AnyRef] => V, prefetch: Int, sources: Publisher[_ <: T]*) = Flux(JFlux.combineLatest(combinator, prefetch, sources: _*))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, V](source1: Publisher[_ <: T1],
                               source2: Publisher[_ <: T2],
                               combinator: (T1, T2) => V) = Flux(JFlux.combineLatest[T1, T2, V](source1, source2, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, V](source1: Publisher[_ <: T1],
                                   source2: Publisher[_ <: T2],
                                   source3: Publisher[_ <: T3],
                                   combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, V](source1, source2, source3, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, V](source1: Publisher[_ <: T1],
                                       source2: Publisher[_ <: T2],
                                       source3: Publisher[_ <: T3],
                                       source4: Publisher[_ <: T4],
                                       combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, V](source1, source2, source3, source4, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param source5    The fifth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, T5, V](source1: Publisher[_ <: T1],
                                           source2: Publisher[_ <: T2],
                                           source3: Publisher[_ <: T3],
                                           source4: Publisher[_ <: T4],
                                           source5: Publisher[_ <: T5],
                                           combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, T5, V](source1, source2, source3, source4, source5, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param source3    The third upstream [[Publisher]] to subscribe to.
    * @param source4    The fourth upstream [[Publisher]] to subscribe to.
    * @param source5    The fifth upstream [[Publisher]] to subscribe to.
    * @param source6    The sixth upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam V  The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T1, T2, T3, T4, T5, T6, V](source1: Publisher[_ <: T1],
                                               source2: Publisher[_ <: T2],
                                               source3: Publisher[_ <: T3],
                                               source4: Publisher[_ <: T4],
                                               source5: Publisher[_ <: T5],
                                               source6: Publisher[_ <: T6],
                                               combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest[T1, T2, T3, T4, T5, T6, V](source1, source2, source3, source4, source5, source6, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The list of upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T The common base type of the source sequences
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T, V](sources: Iterable[Publisher[T]], combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest(sources, combinator))

  /**
    * Build a [[Flux]] whose data are generated by the combination of the most recent published values from all
    * publishers.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/combinelatest.png"
    * alt="">
    *
    * @param sources    The list of upstream [[Publisher]] to subscribe to.
    * @param prefetch   demand produced to each combined source [[Publisher]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam T The common base type of the source sequences
    * @tparam V The produced output after transformation by the given combinator
    * @return a [[Flux]] based on the produced value
    */
  def combineLatest[T, V](sources: Iterable[Publisher[T]], prefetch: Int, combinator: Array[AnyRef] => V) = Flux(JFlux.combineLatest(sources, prefetch, combinator))

  /**
    * Concat all sources pulled from the supplied
    * [[Iterator]] on [[Publisher.subscribe]] from the passed [[Iterable]] until [[Iterator.hasNext]]
    * returns false. A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concat[T](sources: Iterable[Publisher[T]]) = Flux(JFlux.concat(sources))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concat[T](sources: Publisher[Publisher[T]]): Flux[T] = Flux(JFlux.concat(sources: Publisher[Publisher[T]]))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources  The [[Publisher]] of [[Publisher]] to concat
    * @param prefetch the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concat[T](sources: Publisher[Publisher[T]], prefetch: Int) = Flux(JFlux.concat(sources, prefetch))

  /**
    * Concat all sources pulled from the given [[Publisher]] array.
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    * <p>
    *
    * @param sources The array of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concat[T](sources: Publisher[T]*) = Flux(JFlux.concat(sources: _*))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]]) = Flux(JFlux.concatDelayError[T](sources: Publisher[Publisher[T]]))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources  The [[Publisher]] of [[Publisher]] to concat
    * @param prefetch the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]], prefetch: Int) = Flux(JFlux.concatDelayError(sources, prefetch))

  /**
    * Concat all sources emitted as an onNext signal from a parent [[Publisher]].
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned [[Publisher]] which will stop listening if the main sequence has also completed.
    *
    * Errors will be delayed after the current concat backlog if delayUntilEnd is
    * false or after all sources if delayUntilEnd is true.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concatinner.png" alt="">
    * <p>
    *
    * @param sources       The [[Publisher]] of [[Publisher]] to concat
    * @param delayUntilEnd delay error until all sources have been consumed instead of
    *                      after the current source
    * @param prefetch      the inner source request size
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all inner sources sequences until complete or error
    */
  def concatDelayError[T](sources: Publisher[Publisher[T]], delayUntilEnd: Boolean, prefetch: Int) = Flux(JFlux.concatDelayError(sources, delayUntilEnd, prefetch))

  /**
    * Concat all sources pulled from the given [[Publisher]] array.
    * A complete signal from each source will delimit the individual sequences and will be eventually
    * passed to the returned Publisher.
    * Any error will be delayed until all sources have been concatenated.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/concat.png" alt="">
    * <p>
    *
    * @param sources The [[Publisher]] of [[Publisher]] to concat
    * @tparam T The source type of the data sequence
    * @return a new [[Flux]] concatenating all source sequences
    */
  def concatDelayError[T](sources: Publisher[T]*) = Flux(JFlux.concatDelayError(sources: _*))

  /**
    * Creates a Flux with multi-emission capabilities (synchronous or asynchronous) through
    * the FluxSink API.
    * <p>
    * This Flux factory is useful if one wants to adapt some other a multi-valued async API
    * and not worry about cancellation and backpressure. For example:
    * <p>
    * Handles backpressure by buffering all signals if the downstream can't keep up.
    *
    * <pre><code>
    * Flux.String&gt;create(emitter -&gt; {
    *
    * ActionListener al = e -&gt; {
    *         emitter.next(textField.getText());
    * };
    * // without cancellation support:
    *
    *     button.addActionListener(al);
    *
    * // with cancellation support:
    *
    *     button.addActionListener(al);
    *     emitter.setCancellation(() -> {
    *         button.removeListener(al);
    * });
    * });
    * <code></pre>
    *
    * @tparam T the value type
    * @param emitter the consumer that will receive a FluxSink for each individual Subscriber.
    * @return a [[Flux]]
    */
  def create[T](emitter: FluxSink[T] => Unit) = Flux(JFlux.create[T](emitter))

  /**
    * Creates a Flux with multi-emission capabilities (synchronous or asynchronous) through
    * the FluxSink API.
    * <p>
    * This Flux factory is useful if one wants to adapt some other a multi-valued async API
    * and not worry about cancellation and backpressure. For example:
    *
    * <pre><code>
    * Flux.&lt;String&gt;create(emitter -&gt; {
    *
    * ActionListener al = e -&gt; {
    *         emitter.next(textField.getText());
    * };
    * // without cancellation support:
    *
    *     button.addActionListener(al);
    *
    * // with cancellation support:
    *
    *     button.addActionListener(al);
    *     emitter.setCancellation(() -> {
    *         button.removeListener(al);
    * });
    * }, FluxSink.OverflowStrategy.LATEST);
    * <code></pre>
    *
    * @tparam T the value type
    * @param backpressure the backpressure mode, see { @link OverflowStrategy} for the
    *                     available backpressure modes
    * @param emitter      the consumer that will receive a FluxSink for each individual Subscriber.
    * @return a [[Flux]]
    */
  //  TODO: How to test backpressure?
  def create[T](emitter: FluxSink[T] => Unit, backpressure: OverflowStrategy) = Flux(JFlux.create[T](emitter, backpressure))

  /**
    * Supply a [[Publisher]] everytime subscribe is called on the returned flux. The passed [[scala.Function1[Unit,Publisher[T]]]]
    * will be invoked and it's up to the developer to choose to return a new instance of a [[Publisher]] or reuse
    * one effectively behaving like [[Flux.from]]
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/defer.png" alt="">
    *
    * @param supplier the [[Publisher]] Supplier to call on subscribe
    * @tparam T the type of values passing through the [[Flux]]
    * @return a deferred [[Flux]]
    */
  def defer[T](supplier: () => Publisher[T]): Flux[T] = {
    Flux(JFlux.defer(supplier))
  }

  /**
    * Create a [[Flux]] that completes without emitting any item.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/empty.png" alt="">
    * <p>
    *
    * @tparam T the reified type of the target [[Subscriber]]
    * @return an empty [[Flux]]
    */
  def empty[T](): Flux[T] = Flux(JFlux.empty[T]())

  /**
    * Create a [[Flux]] that completes with the specified error.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/error.png" alt="">
    * <p>
    *
    * @param error the error to signal to each [[Subscriber]]
    * @tparam T the reified type of the target [[Subscriber]]
    * @return a new failed  [[Flux]]
    */
  def error[T](error: Throwable): Flux[T] = Flux(JFlux.error[T](error))

  /**
    * Build a [[Flux]] that will only emit an error signal to any new subscriber.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/errorrequest.png" alt="">
    *
    * @param throwable     the error to signal to each [[Subscriber]]
    * @param whenRequested if true, will onError on the first request instead of subscribe().
    * @tparam O the output type
    * @return a new failed [[Flux]]
    */
  def error[O](throwable: Throwable, whenRequested: Boolean): Flux[O] = Flux(JFlux.error(throwable, whenRequested))

  /**
    * Select the fastest source who emitted first onNext or onComplete or onError
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/firstemitting.png" alt="">
    * <p> <p>
    *
    * @param sources The competing source publishers
    * @tparam I The source type of the data sequence
    * @return a new [[Flux}]] eventually subscribed to one of the sources or empty
    */
  def firstEmitting[I](sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.firstEmitting(sources: _*))

  /**
    * Select the fastest source who won the "ambiguous" race and emitted first onNext or onComplete or onError
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/firstemitting.png" alt="">
    * <p> <p>
    *
    * @param sources The competing source publishers
    * @tparam I The source type of the data sequence
    * @return a new [[Flux}]] eventually subscribed to one of the sources or empty
    */
  def firstEmitting[I](sources: Iterable[Publisher[_ <: I]]): Flux[I] = Flux(JFlux.firstEmitting[I](sources))

  /**
    * Expose the specified [[Publisher]] with the [[Flux]] API.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/from.png" alt="">
    * <p>
    *
    * @param source the source to decorate
    * @tparam T the source sequence type
    * @return a new [[Flux]]
    */
  def from[T](source: Publisher[_ <: T]): Flux[T] = {
    Flux(JFlux.from(source))
  }

  /**
    * Create a [[Flux]] that emits the items contained in the provided [[scala.Array]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/fromarray.png" alt="">
    * <p>
    *
    * @param array the array to read data from
    * @tparam T the [[Publisher]] type to stream
    * @return a new [[Flux]]
    */
  def fromArray[T <: AnyRef](array: Array[T]): Flux[T] = {
    Flux(JFlux.fromArray[T](array))
  }

  /**
    * Create a new [[Flux]] that emits the specified items and then complete.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/justn.png" alt="">
    * <p>
    *
    * @param data the consecutive data objects to emit
    * @tparam T the emitted data type
    * @return a new [[Flux]]
    */
  def just[T](data: T*): Flux[T] = {
    Flux(JFlux.just(data: _*))
  }

  /**
    * Create a new [[Flux]] that will only emit the passed data then onComplete.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/just.png" alt="">
    * <p>
    *
    * @param data the unique data to emit
    * @tparam T the emitted data type
    * @return a new [[Flux]]
    */
  def just[T](data: T): Flux[T] = Flux(JFlux.just[T](data))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into an interleaved merged sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergeinner.png" alt="">
    * <p>
    *
    * @param source a [[Publisher]] of [[Publisher]] sequence to merge
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  //  TODO: How to test merge(...)?
  def merge[T](source: Publisher[Publisher[_ <: T]]): Flux[T] = Flux(JFlux.merge[T](source))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into an interleaved merged sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergeinner.png" alt="">
    * <p>
    *
    * @param source      a [[Publisher]] of [[Publisher]] sequence to merge
    * @param concurrency the request produced to the main source thus limiting concurrent merge backlog
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  def merge[T](source: Publisher[Publisher[_ <: T]], concurrency: Int): Flux[T] = Flux(JFlux.merge[T](source, concurrency))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into an interleaved merged sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergeinner.png" alt="">
    * <p>
    *
    * @param source      a [[Publisher]] of [[Publisher]] sequence to merge
    * @param concurrency the request produced to the main source thus limiting concurrent merge backlog
    * @param prefetch    the inner source request size
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  def merge[T](source: Publisher[Publisher[_ <: T]], concurrency: Int, prefetch: Int): Flux[T] = Flux(JFlux.merge[T](source, concurrency, prefetch))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] into an interleaved merged sequence.
    * [[Iterable.iterator()]] will be called for each [[Publisher.subscribe]].
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources the [[scala.Iterable]] to lazily iterate on [[Publisher.subscribe]]
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](sources: Iterable[Publisher[_ <: I]]): Flux[I] = Flux(JFlux.merge[I](sources))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] array into an interleaved merged
    * sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.merge[I](sources))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] array into an interleaved merged
    * sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources  the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @param prefetch the inner source request size
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](prefetch: Int, sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.merge[I](prefetch, sources: _*))

  /**
    * Merge emitted [[Publisher]] sequences from the passed [[Publisher]] array into an interleaved merged
    * sequence.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/merge.png" alt="">
    * <p>
    *
    * @param sources    the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @param prefetch   the inner source request size
    * @param delayError should any error be delayed after current merge backlog
    * @tparam I The source type of the data sequence
    * @return a fresh Reactive [[Flux]] publisher ready to be subscribed
    */
  def merge[I](prefetch: Int, delayError: Boolean, sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.merge[I](prefetch, delayError, sources: _*))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into
    * an ordered merged sequence. Unlike concat, the inner publishers are subscribed to
    * eagerly. Unlike merge, their emitted values are merged into the final sequence in
    * subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources a [[Publisher]] of [[Publisher]] sequence to merge
    * @tparam T the merged type
    * @return a merged  [[Flux]]
    */
  def mergeSequential[T](sources: Publisher[Publisher[T]]): Flux[T] = Flux(JFlux.mergeSequential[T](sources))

  /**
    * Merge emitted [[Publisher]] sequences by the passed [[Publisher]] into
    * an ordered merged sequence. Unlike concat, the inner publishers are subscribed to
    * eagerly. Unlike merge, their emitted values are merged into the final sequence in
    * subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources        a [[Publisher]] of [[Publisher]] sequence to merge
    * @param delayError     should any error be delayed after current merge backlog
    * @param prefetch       the inner source request size
    * @param maxConcurrency the request produced to the main source thus limiting concurrent merge backlog
    * @tparam T the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[T](sources: Publisher[Publisher[T]], delayError: Boolean, maxConcurrency: Int, prefetch: Int): Flux[T] = {
    Flux(JFlux.mergeSequential[T](sources, delayError, maxConcurrency, prefetch))
  }

  /**
    * Merge a number of [[Publisher]] sequences into an ordered merged sequence.
    * Unlike concat, the inner publishers are subscribed to eagerly. Unlike merge, their
    * emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources a number of [[Publisher]] sequences to merge
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](sources: Publisher[_ <: I]*): Flux[I] = Flux(JFlux.mergeSequential(sources: _*))

  /**
    * Merge a number of [[Publisher]] sequences into an ordered merged sequence.
    * Unlike concat, the inner publishers are subscribed to eagerly. Unlike merge, their
    * emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param delayError should any error be delayed after current merge backlog
    * @param prefetch   the inner source request size
    * @param sources    a number of [[Publisher]] sequences to merge
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](prefetch: Int, delayError: Boolean, sources: Publisher[_ <: I]*): Flux[I] =
    Flux(JFlux.mergeSequential(prefetch, delayError, sources: _*))

  /**
    * Merge [[Publisher]] sequences from an [[Iterable]] into an ordered merged
    * sequence. Unlike concat, the inner publishers are subscribed to eagerly. Unlike
    * merge, their emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources an [[Iterable]] of [[Publisher]] sequences to merge
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](sources: Iterable[Publisher[_ <: I]]): Flux[I] = Flux(JFlux.mergeSequential[I](sources))

  /**
    * Merge [[Publisher]] sequences from an [[Iterable]] into an ordered merged
    * sequence. Unlike concat, the inner publishers are subscribed to eagerly. Unlike
    * merge, their emitted values are merged into the final sequence in subscription order.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/mergesequential.png" alt="">
    * <p>
    *
    * @param sources        an [[Iterable]] of [[Publisher]] sequences to merge
    * @param delayError     should any error be delayed after current merge backlog
    * @param maxConcurrency the request produced to the main source thus limiting concurrent merge backlog
    * @param prefetch       the inner source request size
    * @tparam I the merged type
    * @return a merged [[Flux]]
    */
  def mergeSequential[I](sources: Iterable[Publisher[_ <: I]], delayError: Boolean, maxConcurrency: Int, prefetch: Int): Flux[I] =
    Flux(JFlux.mergeSequential[I](sources, delayError, maxConcurrency, prefetch))

  /**
    * Create a [[Flux]] that will never signal any data, error or completion signal.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/never.png" alt="">
    * <p>
    *
    * @tparam T the [[Subscriber]] type target
    * @return a never completing [[Flux]]
    */
  def never[T]() = Flux(JFlux.never[T]())

  /**
    * Build a [[Flux]] that will only emit a sequence of incrementing integer from `start` to `start + count` then complete.
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/range.png" alt="">
    *
    * @param start the first integer to be emit
    * @param count the number ot times to emit an increment including the first value
    * @return a ranged [[Flux]]
    */
  def range(start: Int, count: Int) = Flux(JFlux.range(start, count))

  /**
    * Build a [[reactor.core.publisher.FluxProcessor]] whose data are emitted by the most recent emitted [[Publisher]]. The
    * [[Flux]] will complete once both the publishers source and the last switched to [[Publisher]] have completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/switchonnext.png"
    * alt="">
    *
    * @param mergedPublishers The { @link Publisher} of switching [[Publisher]] to subscribe to.
    * @tparam T the produced type
    * @return a [[reactor.core.publisher.FluxProcessor]] accepting publishers and producing T
    */
  //  TODO: How to test these switchOnNext?
  def switchOnNext[T](mergedPublishers: Publisher[Publisher[_ <: T]]) = Flux(JFlux.switchOnNext[T](mergedPublishers))

  /**
    * Build a [[reactor.core.publisher.FluxProcessor]] whose data are emitted by the most recent emitted [[Publisher]]. The
    * [[Flux]] will complete once both the publishers source and the last switched to [[Publisher]] have completed.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/projectreactor.io/master/src/main/static/assets/img/marble/switchonnext.png"
    * alt="">
    *
    * @param mergedPublishers The { @link Publisher} of switching { @link Publisher} to subscribe to.
    * @param prefetch         the inner source request size
    * @tparam T the produced type
    * @return a [[reactor.core.publisher.FluxProcessor]] accepting publishers and producing T
    */
  def switchOnNext[T](mergedPublishers: Publisher[Publisher[_ <: T]], prefetch: Int) = Flux(JFlux.switchOnNext[T](mergedPublishers, prefetch))

  /**
    * Uses a resource, generated by a supplier for each individual Subscriber, while streaming the values from a
    * Publisher derived from the same resource and makes sure the resource is released if the sequence terminates or
    * the Subscriber cancels.
    * <p>
    * Eager resource cleanup happens just before the source termination and exceptions raised by the cleanup Consumer
    * may override the terminal even.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/using.png"
    * alt="">
    *
    * @param resourceSupplier a [[java.util.concurrent.Callable]] that is called on subscribe
    * @param sourceSupplier   a [[Publisher]] factory derived from the supplied resource
    * @param resourceCleanup  invoked on completion
    * @tparam T emitted type
    * @tparam D resource type
    * @return new [[Flux]]
    */
  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => Publisher[_ <: T], resourceCleanup: D => Unit): Flux[T] =
    Flux(JFlux.using[T, D](resourceSupplier, sourceSupplier, resourceCleanup))

  /**
    * Uses a resource, generated by a supplier for each individual Subscriber, while streaming the values from a
    * Publisher derived from the same resource and makes sure the resource is released if the sequence terminates or
    * the Subscriber cancels.
    * <p>
    * <ul> <li>Eager resource cleanup happens just before the source termination and exceptions raised by the cleanup
    * Consumer may override the terminal even.</li> <li>Non-eager cleanup will drop any exception.</li> </ul>
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/using.png"
    * alt="">
    *
    * @param resourceSupplier a [[java.util.concurrent.Callable]] that is called on subscribe
    * @param sourceSupplier   a [[Publisher]] factory derived from the supplied resource
    * @param resourceCleanup  invoked on completion
    * @param eager            true to clean before terminating downstream subscribers
    * @tparam T emitted type
    * @tparam D resource type
    * @return new Stream
    */
  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => Publisher[_ <: T], resourceCleanup: D => Unit, eager: Boolean): Flux[T] =
    Flux(JFlux.using[T, D](resourceSupplier, sourceSupplier, resourceCleanup, eager))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    * <p>
    *
    * @param source1    The first upstream [[Publisher]] to subscribe to.
    * @param source2    The second upstream [[Publisher]] to subscribe to.
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the
    *                   value to signal downstream
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam O  The produced output after transformation by the combinator
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, O](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], combinator: (T1, T2) => O) =
    Flux(JFlux.zip[T1, T2, O](source1, source2, combinator))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2]): Flux[(T1, T2)] =
    Flux(JFlux.zip[T1, T2](source1, source2))
      .map(tupleTwo2ScalaTuple2)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3]): Flux[(T1, T2, T3)] =
    Flux(JFlux.zip[T1, T2, T3](source1, source2, source3))
      .map(tupleThree2ScalaTuple3)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @param source4 The fourth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3, T4](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4]): Flux[(T1, T2, T3, T4)] =
    Flux(JFlux.zip[T1, T2, T3, T4](source1, source2, source3, source4))
      .map(tupleFour2ScalaTuple4)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @param source4 The fourth upstream [[Publisher]] to subscribe to.
    * @param source5 The fifth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3, T4, T5](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5]): Flux[(T1, T2, T3, T4, T5)] =
    Flux(JFlux.zip[T1, T2, T3, T4, T5](source1, source2, source3, source4, source5))
      .map(tupleFive2ScalaTuple5)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zipt.png" alt="">
    * <p>
    *
    * @param source1 The first upstream [[Publisher]] to subscribe to.
    * @param source2 The second upstream [[Publisher]] to subscribe to.
    * @param source3 The third upstream [[Publisher]] to subscribe to.
    * @param source4 The fourth upstream [[Publisher]] to subscribe to.
    * @param source5 The fifth upstream [[Publisher]] to subscribe to.
    * @param source6 The sixth upstream [[Publisher]] to subscribe to.
    * @tparam T1 type of the value from source1
    * @tparam T2 type of the value from source2
    * @tparam T3 type of the value from source3
    * @tparam T4 type of the value from source4
    * @tparam T5 type of the value from source5
    * @tparam T6 type of the value from source6
    * @return a zipped [[Flux]]
    */
  def zip[T1, T2, T3, T4, T5, T6](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5], source6: Publisher[_ <: T6]): Flux[(T1, T2, T3, T4, T5, T6)] =
    Flux(JFlux.zip[T1, T2, T3, T4, T5, T6](source1, source2, source3, source4, source5, source6))
      .map(tupleSix2ScalaTuple6)

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    *
    * The [[Iterable.iterator]] will be called on each [[Publisher.subscribe]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    *
    * @param sources    the [[Iterable]] to iterate on [[Publisher.subscribe]]
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[O](sources: Iterable[_ <: Publisher[_]], combinator: Array[_] => O) =
    Flux(JFlux.zip[O](sources, combinator))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    *
    * The [[Iterable.iterator]] will be called on each [[Publisher.subscribe]].
    *
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    *
    * @param sources    the [[Iterable]] to iterate on [[Publisher.subscribe]]
    * @param prefetch   the inner source request size
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the value
    *                   to signal downstream
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[O](sources: Iterable[_ <: Publisher[_]], prefetch: Int, combinator: Array[_] => O) =
    Flux(JFlux.zip[O](sources, prefetch, combinator))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    * <p>
    *
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the
    *                   value to signal downstream
    * @param sources    the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @tparam I the type of the input sources
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[I, O](combinator: Array[AnyRef] => O, sources: Publisher[_ <: I]*) =
    Flux(JFlux.zip[I, O](combinator, sources: _*))

  /**
    * "Step-Merge" especially useful in Scatter-Gather scenarios. The operator will forward all combinations
    * produced by the passed combinator function of the
    * most recent items emitted by each source until any of them completes. Errors will immediately be forwarded.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/zip.png" alt="">
    * <p>
    *
    * @param combinator The aggregate function that will receive a unique value from each upstream and return the
    *                   value to signal downstream
    * @param prefetch   individual source request size
    * @param sources    the [[Publisher]] array to iterate on [[Publisher.subscribe]]
    * @tparam I the type of the input sources
    * @tparam O the combined produced type
    * @return a zipped [[Flux]]
    */
  def zip[I, O](combinator: Array[AnyRef] => O, prefetch: Int, sources: Publisher[_ <: I]*) =
    Flux(JFlux.zip[I, O](combinator, prefetch, sources: _*))

  /**
    * Create a [[Flux]] that emits the items contained in the provided [[Iterable]].
    * A new iterator will be created for each subscriber.
    * <p>
    * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.0.5.RELEASE/src/docs/marble/fromiterable.png" alt="">
    * <p>
    *
    * @param it the [[Iterable]] to read data from
    * @tparam T the [[Iterable]] type to stream
    * @return a new [[Flux]]
    */
  def fromIterable[T](it: Iterable[T]) = Flux(JFlux.fromIterable(it))
}
