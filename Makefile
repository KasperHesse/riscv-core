PORT := COM6
PROGRAM := blink

CCFLAGS = -nostdlib -nostartfiles -march=rv32i -mabi=ilp32 -c
LDFLAGS = -m elf32lriscv -T programs/linker.ld --no-relax

BUILD_DIR = build
C_SRC_DIR = programs/c
ASM_SRC_DIR = programs/asm

C_SRCS = $(wildcard programs/c/*.c)
C_OBJS = $(patsubst programs/c/%.c, build/%.o, $(C_SRCS))
ASM_SRCS = $(wildcard programs/asm/*.S)
ASM_OBJS = $(patsubst programs/asm/%.S, build/%.o, $(ASM_SRCS))

$(BUILD_DIR):
	mkdir -p $@

$(BUILD_DIR)/%.o: $(C_SRC_DIR)/%.c | $(BUILD_DIR)
	riscv64-unknown-elf-gcc.exe $(CCFLAGS) $< -o $@

$(BUILD_DIR)/%.o: $(ASM_SRC_DIR)/%.S | $(BUILD_DIR)
	riscv64-unknown-elf-gcc.exe $(CCFLAGS) -T programs/linker.ld -c $< -o $@

$(BUILD_DIR)/%.elf: $(BUILD_DIR)/$(PROGRAM).o $(BUILD_DIR)/entry.o $(BUILD_DIR)/io.o
	riscv64-unknown-elf-ld.exe $(LDFLAGS) $^ -o $@

$(BUILD_DIR)/%.bin: $(BUILD_DIR)/%.elf
	riscv64-unknown-elf-objcopy.exe -O binary $< $@

.PHONY: builds
builds: $(C_OBJS) $(ASM_OBJS)

.PHONY: comp
comp: $(BUILD_DIR)/$(PROGRAM).elf

.PHONY: bin
bin: $(BUILD_DIR)/$(PROGRAM).bin

.PHONY: download
download: $(BUILD_DIR)/$(PROGRAM).bin
	python tools/downloader.py $(PORT) $<


.PHONY: clean
clean:
	rm -rf programs/*.bin
	rm -rf programs/*.elf
	rm -rf programs/*.o
	rm -rf programs/*.out
	rm -rf programs/*.elf
	rm -rf *.bin
	rm -rf build

.PHONY: debug
debug:
	@echo "C_SRC_DIR = $(C_SRC_DIR)"
	@echo "ASM_SRC_DIR = $(ASM_SRC_DIR)"
	@echo "ASM_SRCS = $(ASM_SRCS)"
