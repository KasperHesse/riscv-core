package core

case class Config(
                 /** XLEN defines the width of all X-registers */
                 XLEN: Int = 32,
                 debug: Boolean = false,
                 pcReset: Long = 0
                 ) {
  require(XLEN == 32, "XLEN Must be 32")
  val WMASKLEN = XLEN/8
}