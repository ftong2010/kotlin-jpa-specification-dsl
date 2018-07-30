package au.com.console.jpaspecificationdsl

import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.domain.Specifications
import java.io.Serializable
import javax.persistence.criteria.*
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1

fun <X, Y> Path<X>.get(property: KProperty0<Y>): Path<Y> = this.get<Y>(property.name)

// Helper to allow joining to Properties
fun <Z, T, R> From<Z, T>.join(prop: KProperty1<T, R?>, joinType: JoinType = JoinType.INNER): Join<T, R> = this.join<T, R>(prop.name, joinType)

fun <Z, X, Y> FetchParent<Z, X>.fetch(prop: KProperty1<X, Y?>, joinType: JoinType = JoinType.INNER): Fetch<X, Y> = this.fetch(prop.name, joinType)

// Helper to enable get by Property
fun <R> Path<*>.get(prop: KProperty1<*, R?>): Path<R> = this.get<R>(prop.name)

fun CriteriaBuilder.and(p: Collection<Predicate>): Predicate = this.and(*p.toTypedArray())

fun <R> CriteriaQuery<*>.asc(path: Path<R>): CriteriaQuery<*> = this.orderBy(OrderImpl(path, true))

fun <R> CriteriaQuery<*>.desc(path: Path<R>): CriteriaQuery<*> = this.orderBy(OrderImpl(path, false))

fun <R> CriteriaBuilder.`in`(path: Path<R>, values: Collection<R>): CriteriaBuilder.In<R> = this.`in`(path).apply { values.forEach { value(it) } }

// Version of Specification.where that makes the CriteriaBuilder implicit
fun <T> where(makePredicate: CriteriaBuilder.(Root<T>) -> Predicate): Specification<T> =
    Specification.where<T> { root, _, criteriaBuilder -> criteriaBuilder.makePredicate(root) }

// helper function for defining Specification that take a Path to a property and send it to a CriteriaBuilder
private fun <T, R> KProperty1<T, R?>.spec(makePredicate: CriteriaBuilder.(path: Path<R>) -> Predicate): Specification<T> =
    this.let { property -> where { root -> makePredicate(root.get(property)) } }

// Equality
fun <T, R> KProperty1<T, R?>.equal(x: R): Specification<T> = spec { equal(it, x) }
fun <T, R> KProperty1<T, R?>.notEqual(x: R): Specification<T> = spec { notEqual(it, x) }

// Ignores empty collection otherwise an empty 'in' predicate will be generated which will never match any results
fun <T, R: Any> KProperty1<T, R?>.`in`(values: Collection<R>): Specification<T> = if (values.isNotEmpty()) spec { path ->
    `in`(path).apply { values.forEach { this.value(it) } }
} else Specifications.where<T>(null)

fun <T, R: Any> KProperty1<T, R?>.`in`(vararg values: R): Specification<T> = if (values.isNotEmpty()) spec { path ->
    `in`(path).apply { values.forEach { this.value(it) } }
} else Specifications.where<T>(null)

// Comparison
fun <T> KProperty1<T, Number?>.le(x: Number) = spec { le(it, x) }
fun <T> KProperty1<T, Number?>.lt(x: Number) = spec { lt(it, x) }
fun <T> KProperty1<T, Number?>.ge(x: Number) = spec { ge(it, x) }
fun <T> KProperty1<T, Number?>.gt(x: Number) = spec { gt(it, x) }
fun <T, R : Comparable<R>> KProperty1<T, R?>.lessThan(x: R) = spec { lessThan(it, x) }
fun <T, R : Comparable<R>> KProperty1<T, R?>.lessThanOrEqualTo(x: R) = spec { lessThanOrEqualTo(it, x) }
fun <T, R : Comparable<R>> KProperty1<T, R?>.greaterThan(x: R) = spec { greaterThan(it, x) }
fun <T, R : Comparable<R>> KProperty1<T, R?>.greaterThanOrEqualTo(x: R) = spec { greaterThanOrEqualTo(it, x) }
fun <T, R : Comparable<R>> KProperty1<T, R?>.between(x: R, y: R) = spec { between(it, x, y) }
fun <T, R : Comparable<R>> KProperty1<T, R?>.between(range: Pair<R, R>) = spec { between(it, range.first, range.second) }
fun <T, R : Comparable<R>> KProperty1<T, R?>.between(range: ClosedRange<R>) = spec { between(it, range.start, range.endInclusive) }

// True/False
fun <T> KProperty1<T, Boolean?>.isTrue() = spec { isTrue(it) }
fun <T> KProperty1<T, Boolean?>.isFalse() = spec { isFalse(it) }

// Null / NotNull
fun <T, R> KProperty1<T, R?>.isNull() = spec { isNull(it) }
fun <T, R> KProperty1<T, R?>.isNotNull() = spec { isNotNull(it) }

// Collections
fun <T, R : Collection<*>> KProperty1<T, R?>.isEmpty() = spec { isEmpty(it) }
fun <T, R : Collection<*>> KProperty1<T, R?>.isNotEmpty() = spec { isNotEmpty(it) }
fun <T, E, R : Collection<E>> KProperty1<T, R?>.isMember(elem: E) = spec { isMember(elem, it) }
fun <T, E, R : Collection<E>> KProperty1<T, R?>.isNotMember(elem: E) = spec { isNotMember(elem, it) }

// Strings
fun <T> KProperty1<T, String?>.like(x: String): Specification<T> = spec { like(it, x) }
fun <T> KProperty1<T, String?>.like(x: String, escapeChar: Char): Specification<T> = spec { like(it, x, escapeChar) }
fun <T> KProperty1<T, String?>.notLike(x: String): Specification<T> = spec { notLike(it, x) }
fun <T> KProperty1<T, String?>.notLike(x: String, escapeChar: Char): Specification<T> = spec { notLike(it, x, escapeChar) }

// And
infix fun <T> Specification<T>.and(other: Specification<T>?): Specification<T> = if(other != null) this.and(other) else this

inline fun <reified T> and(vararg specs: Specification<T>?): Specification<T> {
    return and(specs.toList())
}
inline fun <reified T> and(specs: Iterable<Specification<T>?>): Specification<T> {
    return combineSpecification(specs, Specification<T>::and)
}

fun <T, R> Specification<T>.fetch(vararg props: KProperty1<T, R?>): Specification<T> {
    return and { root, _, criteriaBuilder ->
        props.forEach {
            root.fetch(it)
        }
        return@and criteriaBuilder.and(listOf())
    }
}

infix fun <T, R> Specification<T>.fetch(prop: KProperty1<T, R?>): Specification<T> = fetch(listOf(prop))

infix fun <T, R> Specification<T>.fetch(props: List<KProperty1<T, R?>?>): Specification<T> =
    fetch(*props.filterNotNull().toTypedArray())

infix fun <T, R> Specification<T>.asc(prop: KProperty1<T, R?>): Specification<T> = orderBy(prop to true)

infix fun <T, R> Specification<T>.desc(prop: KProperty1<T, R?>): Specification<T> = orderBy(prop to false)

infix fun <T, R> Specification<T>.orderBy(prop: Pair<KProperty1<T, R?>, Boolean>): Specification<T> {
    return and { root, query, criteriaBuilder ->
        query.orderBy(OrderImpl(root.get(prop.first), prop.second))
        return@and criteriaBuilder.and(listOf())
    }
}

class OrderImpl(
    private val exp: Expression<*>,
    private var asc: Boolean = true
) : Order, Serializable {
    override fun reverse(): Order = this.apply { asc = !asc }
    override fun isAscending(): Boolean = asc
    override fun getExpression(): Expression<*> = exp
}

// Or
infix fun <T> Specification<T>.or(other: Specification<T>) : Specification<T> = this.or(other)
inline fun <reified T> or(vararg specs: Specification<T>?): Specification<T> {
    return or(specs.toList())
}
inline fun <reified T> or(specs: Iterable<Specification<T>?>): Specification<T> {
    return combineSpecification(specs, Specification<T>::or)
}

// Not
operator fun <T> Specification<T>.not(): Specification<T> = Specification.not(this)

// Combines Specification with an operation
inline fun <reified T> combineSpecification(specs: Iterable<Specification<T>?>, operation: Specification<T>.(Specification<T>) -> Specification<T>): Specification<T> {
    return specs.filterNotNull().fold(emptySpecification()) { existing, new -> existing.operation(new) }
}

// Empty Specification
inline fun <reified T> emptySpecification(): Specification<T> = Specifications.where<T>(null)