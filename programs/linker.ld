ENTRY(_start)

MEMORY
{
  INST (rx)  : ORIGIN = 0x0,   LENGTH = 0x100
  DATA (rwx) : ORIGIN = 0x100, LENGTH = 0x100
}

SECTIONS
{
  .data :
  {
    *(.data)
  }> DATA

  .text :
  {
    *(.text)
  }> INST
}