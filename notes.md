A generic RV32I core, that should later be parameterized to also support RV64 and the IMAFDZicsr_Zifencei extensions.

# TODO:
- Implement simple kernel
- Implement trap handler and simple traps (exit, print)
  
# Potential CSR-implementation
- Add module CSRDecode in Decode stage that handles CSR instructions
- Add separate pipeline parallel to ex-mem pipeline for handling CSR read/write operations
  - Should feed into WB stage. No forwarding between the parallel pipelines
  - Should present signals that indicate which registers may be written. If data hazard is detected, stall the ex-mem pipeline

- Centralized control vs handshaking?
  - Centralized control: 
    - Pro: No need for handshaking
    - Pro: Maybe easier to implement
    - Con: Long combinational paths
  - Handshaking
    - Pro: Shorter combinational paths
    - Con: Additional logic for skid buffers required

# Optimizations
- [ ] Move pipeline registers to end-of-stage instead of start-of-stage
  - Will probably make a lot of things easier
  - Example: Shared forwarding logic between CSR pipe and EX pipe
## Idea for implementation
- No need to compute address offsets or similar
- Simplify logic: CSR instructions must exist in a flushed pipeline. When a CSR instruction is decoded
  and exists in EX, IF,ID,EX are stalled until MEM,WB are finished
- If CSR instruction requires value from register file, our stalling logic already ensures that's correct
- Partition Execute into smaller modules? BranchEval, ALU and CSR


# Wishbone implementation
The wishbone interface should decouple the "invalid request" tracking from
the fetch stage, making it the job of the cache module to track that stuff.

We place the cache *between* IF and ID, and don't register the value returned.
Instead, we use it immediately when it is valid (mem.ACK) and also valid from
Fetch stage (fetch.valid).

- Use stall-signal from peripheral to control when new requests are launched.
- 