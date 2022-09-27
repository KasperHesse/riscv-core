A generic RV32I core, that should later be parameterized to also support RV64 and the IMAFDZicsr_Zifencei extensions.

General:
- BRANCHES ARE EVALAUTED IN THE EXECUTE STAGE, requires flushing of IF and ID when taken
- IMMEDIATES ARE GENERATED IN DECODE STAGE
- Stall = keep values in the stage, but don't accept new inputs
  - Sources of stalls:
    - Delayed memory access in mem-stage: Stall all other stages until mem-result arrives
    - Load-use hazard, detected in EX+MEM stages. Stall IF,ID,EX, let MEM+WB progress
- Flush: Discard values in the stage, turning it into a NOP by resetting control signals / instruction
  - Sources of flushes:
    - Delayed memory access in IF stage. Keep sending NOPs while waiting for the output to arrive
    - Branch mispredicted. When branch is evaluated in EX-stage, if mispredicted, flush IF and ID
- Use ready/valid handshake between all stages? Make sure it's not combinationally tied all the way around
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
- We need 3 comparators. rs1 < rs2, rs1 == rs2, rs1 < rs2 (u)
  - BEQ: rs1 == rs2
  - BNE: !(rs1 == rs2)
  - BLT: rs1 < rs2
  - BGE: !(rs1 < rs2)
  - BLTU: rs1 < rs2 (u)
  - BGEU: !(rs1 < rs2 (u))
  - DONE

- Circuit that generates values that will be written into regfile
  - Operating on rs1/rs2 or rs1/imm
    - ADD, SUB, OR, XOR, AND, SLT, SLTU, SLL, SRL, SRA, 
    - DONE
  - Operating on PC and constant 4
    - JAL, JALR
    - We always calculate it, if ctrl.branch is set, we use that value instead of aluOut
    - DONE


- Circuit that generates pc_next value
  - Operating on PC + imm
    - JAL, all branches
  - Operating on rs1 + imm
    - JALR, must set LSB of result to 0
- DONE

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
