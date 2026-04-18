#!/usr/bin/env python


"""

Download data from a Rigol DS1052E oscilloscope and graph with matplotlib.

By Ken Shirriff, http://righto.com/rigol



Based on http://www.cibomahto.com/2010/04/controlling-a-rigol-oscilloscope-using-linux-and-python/

by Cibo Mahto.

"""

import numpy as np
import matplotlib.pyplot as plot
import sys
import pyvisa as visa
import time
import serial
import struct
import os

# Define the bytes to send

fixed_bytes = bytes([0x01, 0x23, 0xab, 0xcd])

def connect_to_target():

    # Open the serial port to the target...
    ser = serial.Serial('COM3', baudrate=38400)
    time.sleep(2)
    return ser


def communicate_with_target(ser):

    # Send the header first...
    ser.write(fixed_bytes)

    # Generate 16 random bytes in plaintext_bytes
    plaintext_bytes = os.urandom(16)
    ser.write(plaintext_bytes)
    plaintext_value = 0

    # Convert the received bytes to a byte array and calculate the hexadecimal value...

    # Read the 20 bytes from the serial port
    ciphertext_bytes = ser.read(20)

    # Convert the received bytes to a byte array and take the last 16 bytes
    ciphertext_bytes = ciphertext_bytes[4:]

    # Convert the ciphertext bytes to an integer value
    ciphertext_value = bytearray(ciphertext_bytes)
    plaintext_value = int.from_bytes(plaintext_bytes)

    ciphertext_value = 0
    i=0

    for byte in ciphertext_bytes:
        ciphertext_value = ciphertext_value | (byte << (8*i))
        i=i+1

    # Print the ciphertext value in hexadecimal format
    print("Plain Text:", hex(plaintext_value), "    Ciphertext Value (Hex):", hex(ciphertext_value))
    return plaintext_value, ciphertext_value

# Main Function Starts here...

def main():

    # Connect to target...
    ser_target = connect_to_target()

    num_traces = 10000

    for i in range(0, num_traces):

        # Communicate with Target to send plaintext and receive Ciphertext...
        plaintext, ciphertext = communicate_with_target(ser_target)
        time.sleep(0.1)

main()
