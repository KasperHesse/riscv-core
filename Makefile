PORT := COM6
PROGRAM := blink

.PRECIOUS: programs/%.elf

# Need -nostdlib and -nostartfiles to run bare-metal code
# Need march=rv32i and mabi=ilp32 to ensure only rv32i instructions are generated and correct calling conventions
# Need linker script to correctly map data into memory
# Need -Wl,--no-relax to remove errors when placing .text at 0x00.
programs/%.elf: programs/%.S
	riscv64-unknown-elf-gcc.exe -nostdlib -nostartfiles -march=rv32i -mabi=ilp32 \
	-T programs/linker.ld -Wl,--no-relax $< -o $@

programs/%.bin: programs/%.elf
	riscv64-unknown-elf-objcopy.exe -O binary $< $@

comp: programs/$(PROGRAM).elf programs/$(PROGRAM).bin

download: comp
	python tools/downloader.py $(PORT) $(PROGRAM)

clean:
	rm -rf programs/*.bin
	rm -rf programs/*.elf
	rm -rf programs/*.o
	rm -rf programs/*.out
	rm -rf programs/*.elf