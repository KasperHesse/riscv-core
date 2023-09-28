ENTRY(_start)
OUTPUT_FORMAT(elf32-littleriscv)

SECTIONS
{
  .text : {
    * (.text.start) /* Must have this first to ensure bare-metal entrypoint at 0x0 works correctly */
    * (.text)
  }

  .rodata : ALIGN(4) { /* Strings and other read-only data */
    * (.rodata)
    * (.sdata)
  }

  .data : ALIGN(4)
  {
    *(.data)
  }

  /* Core has been synthesized with 2048-word memory. Initialize sp to top of memory */
  /* TODO: Set this programmatically based off configuration file */
  . = 2048*4;
  stack_top = .;

  /* Discard all other sections. Includes .note.gnu.build-id generated when using linux-gnu-gcc */
  /* TODO: Handle .bss segment correctly once we generate code that uses .bss */
  /DISCARD/ : {
    *(*)
  }
}