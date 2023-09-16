import serial
import sys

def do_download():
  print(sys.argv)
  with open(f"programs/{sys.argv[2]}.bin", "rb") as f:
    port = sys.argv[1]
    print(f"Opening serial port {port}")
    ser = serial.Serial(port, 115200, timeout=1)
    tx = f.read()
    bundles = [tx[i:i+255] for i in range(0, len(tx), 255)]
    for bnd in bundles:
      ser.write(bytes([len(bnd)]))
      ser.write(bnd)
      print(len(bnd))
      print(bnd)
      print()

    if (len(bundles[-1]) == 255): #If last bundle was a full block, send 0 to activate go
      ser.write(bytes([0]))
      print("Wrote 0 for last block")
    print("Finished writing data")
    print(f"Data from OS UART buffer: {ser.read_all()}")
  # port = serial.Serial(sys.argv[1], 115200, timeout=1)
  # print(port.read(8))

if __name__ == "__main__":
  do_download()