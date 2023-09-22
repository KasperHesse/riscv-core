ENTRY(_start)
OUTPUT_FORMAT(elf32-littleriscv)

SECTIONS
{
  .text : {
    * (.text)
  }

  .data :
  {
    *(.data)
  }

  /DISCARD/ : {
    *(*) /*We don't want .note.gnu.build-id generated when using linux-gnu-gcc */
  }
}