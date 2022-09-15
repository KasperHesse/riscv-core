A generic RV32I core, that should later be parameterized to also support RV64 and the IMAFDZicsr_Zifencei extensions.

General:
- BRANCHES ARE EVALAUTED IN THE EXECUTE STAGE, requires flushing of IF and ID when taken
- IMMEDIATES ARE GENERATED IN DECODE STAGE
-

# Stages
## core.Fetch
core.Fetch stage contains the PC and fetches instructions from an external instruction memory / instruction cache.

IO:
- To imem / icache
  - addr: output, address to access
  - req: output, flag indicating that we actually want to access this memory location
  - instr: input, instruction word fetched from mem[addr]
  - ack: input, acknowledge indicating that the desired instruction word can be sampled on this CC
- To decode stage
  - instr: output, the instruction word
  - pc: output, the PC value for that instruction word
- Control signals
  - loadPC: input, asserted when a branch or jump is executed and new PC should be loaded
  - nextPC: input, the new pc to go to when loadPC is asserted
  - 

## Decode
- Control signals
  - flush: input, asserted when instruction should be converted to a NOP

## riscv_core.Execute
riscv_core.Execute stage uses a separate ALU to compute the branch/jump offset as the immediate plus the rs1 value.
Also uses separate comparators for rs1, rs2 values to determine if branch should be taken.
- Only requires two comparators: rs1 < rs2 and rs1 == rs2. If neither is true, rs1 > rs2. if just equal, rs2 >= rs2
- Or perhaps more, to also implement BLTU, BGEU

- Control signals
  - op2src: whether the second operand comes from regfile (0) or sign-ext immediate (1)

## core.Memory

- IO
  - addr: output address to access
  - req: output, flag indicating that we actually want to access this memory location
  - data (XLEN): input, data word fetched from mem[addr]
  - ack: input, acknowledge indicating that the desired instruction word can be sampled on this CC
- Control signals
  - writeMem: input, whether memory should be written
  - readMem: input, whether memory should be read

## core.Writeback
- Control signals
  - regWrite: Whether to write the result from the mem-stage into regfile

# Others
## Hazard detection
The only hazard in the RV32I processor is the load-use hazard, which generates a stall in the IF, ID stages.
This can be detected when the LD instruction is in the EX stage and the USE instruction in the ID stage, only requiring a stall of ID
and IF.

## Branch flushing
