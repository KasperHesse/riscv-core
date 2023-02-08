A generic RV32I core, that should later be parameterized to also support RV64 and the IMAFDZicsr_Zifencei extensions.

# TODO:
- Add explicit valid/ready signalling between pipeline stages. Instead of passing NOP's into Decode from Fetch,
  simply take valid low. Valid should then propagate through the remaining pipeline stages.
  - This also allows us to stall the pipeline without an explicit stall signal: If ready is not high
    (eg delayed memory load), we don't propagate from ID into EX
  - Use of stall/flush are for special circumstances (e.g. load-use hazard). If delayed memory load, that should
    be resolvable without manual intervention
- Would be nice if all signals propagated forward together (instruction stored in IF/ID pipeline reg)
  - In current impl, requires double-buffering of pc
- Stall IF,ID,EX if memory load/store operation does not process in one clock cycle
- Send NOPs and keep PC constant if new instruction is not fetched in one clock cycle
- Implement trap handler and simple traps (exit, print)
  - Add extra signal `trap` which is high when trapped. When trapping, if `mcause` contains a given value (TBD)
    we exit simulation early.
  - print-trap should not be in software, but should be a trap handler at predefined memory location.

Current problem: io.dmem.in.ack is combinationally tied to io.imem.out.addr through the hazard detection module
that stalls the IF stage. This breaks ChiselTest.
- Potential fix. When ID stage is stalled, switch to operating on instruction saved in register instead of 
  value received from I$. This keeps output correct.
- When ID is de-stalled, it should send out stalled instruction on that same CC, take instruction from I$ 
  on next CC

cycle 0: Instruction A enters ID stage
cycle 1: Instruction B enters. Halfway through, ID.stall is asserted
cycle 2: Instruction B is in register, flag has been set high. ID.stall is still asserted
cycle 3: --||--
cycle 4: Instrutction B is in register, flag is still high. ID.stall is deasserted at some point
cycle 5: Instruction C enters from I$. Flag has been set low
  - flagReg = RegNext(io.hzd.stall)

In IF stage
cycle 1: Instruction C request is launched. Stall arrives
cycle 2: Instruction C request is kept constant instead of moving on to instruction D
cycle 3: --||--
cycle 4: IF.stall is deasserted. Instruction C request is kept constant
cycle 5: Instruction C enters from I$. Instruction D request is launched.

This would decouple the IMEM address and DMEM ack

Current problem: With two items forked out to handle dmem requests, things get messy
- Instead of registering multiple modules, register functions with the DmemDriver.
  If the address matches their address, they get to handle the port for as long as they wish

# Stages
## core.stages.Fetch
core.stages.Fetch stage contains the PC and fetches instructions from an external instruction memory / instruction cache.

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

## core.stages.Memory

- IO
  - addr: output address to access
  - req: output, flag indicating that we actually want to access this memory location
  - rdata (XLEN): input, data word fetched from mem[addr]
  - wdata (XLEN): output, data word to write to mem[addr]
  - ack: input, acknowledge indicating that the desired instruction word can be sampled on the NEXT cc
- Control signals
  - writeMem: input, whether memory should be written
  - readMem: input, whether memory should be read

## core.stages.Writeback
- Control signals
  - we: Whether to write the result from the mem-stage into regfile
  - memRead: Whether to use result from mem-stage or ex-stage as the final result

# Others
## Hazard detection
The only hazard in the RV32I processor is the load-use hazard, which generates a stall in the IF, ID stages.
This can be detected when the LD instruction is in the EX stage and the USE instruction in the ID stage, only requiring a stall of ID
and IF.

## Forwarding
Values can be forwarded to EX from MEM and WB. 
- WB can forward its result
- MEM can only forward the value received from ex.res, and not the value being fetched from memory. 

## Branch flushing
