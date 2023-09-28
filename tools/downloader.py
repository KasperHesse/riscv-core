import serial
import argparse

def do_download():
  parser = argparse.ArgumentParser(
    description="Download binary files over UART to a RISC-V core",
    formatter_class=argparse.ArgumentDefaultsHelpFormatter
  )
  parser.add_argument("port", type=str, help="Serial port over which the file should be downloaded")
  parser.add_argument("path", type=str, help="Relative path to binary file that should be downloaded. Relative to directory where downloader is run")
  parser.add_argument("-b", "--baudrate", type=int, default=115200, help="Baudrate of the serial connection that is used for downloading")

  args = parser.parse_args()
  with open(args.path, "rb") as f:
    print(f"Opening serial port {args.port} with baudrate {args.baudrate}")
    ser = serial.Serial(args.port, args.baudrate, timeout=1)
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

if __name__ == "__main__":
  do_download()