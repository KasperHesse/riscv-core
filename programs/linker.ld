ENTRY(_start)
OUTPUT_FORMAT(elf32-littleriscv)

MEMORY {
  ONCHIP (rwx) : ORIGIN = 0x0, LENGTH = 0x100 /*Length 0x100 is just a guess for now */
}

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
    *(.note.gnu.build-id) /*Generated with riscv64-linux-gnu-gcc, not with unknown-elf */
  }
}