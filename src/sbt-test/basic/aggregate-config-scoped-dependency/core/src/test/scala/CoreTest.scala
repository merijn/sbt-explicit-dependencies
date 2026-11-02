import cats.kernel.Eq

object CoreTest {
  println(Eq.fromUniversalEquals[Int].eqv(1, 1))
}
