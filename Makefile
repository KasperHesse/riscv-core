PORT := COM6
PROGRAM := blink

programs/%.out: programs/%.S
	riscv64-unknown-elf-gcc.exe -nostdlib -nostartfiles -march=rv32i -mabi=ilp32 -T programs/linker.ld $< -o $@

programs/%.bin: programs/%.out
	riscv64-unknown-elf-objcopy.exe -O binary $< $@

comp: programs/$(PROGRAM).out programs/$(PROGRAM).bin

download: comp
	python tools/downloader.py $(PORT) $(PROGRAM)

clean:
	rm -rf programs/*.bin
	rm -rf programs/*.elf
	rm -rf programs/*.o
	rm -rf programs/*.out
	rm -rf programs/*.elf