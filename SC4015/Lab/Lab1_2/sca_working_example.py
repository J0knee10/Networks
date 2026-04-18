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


def connect_to_scope():
    rm = visa.ResourceManager()
    instruments = rm.list_resources()
    print(instruments)
    # Get the USB device, e.g. 'USB0::0x1AB1::0x0588::DS1ED141904883'
    usb = list(filter(lambda x: 'USB' in x, instruments))
    scope = rm.open_resource(usb[0], timeout=100000, chunk_size=1024000)  # bigger timeout for long mem
    return scope


def configure_scope(scope):
    # Setting unlimited Bandwidth...
    scope.write(":CHAN1:BWL OFF")

    # Turning Display for both channels..
    scope.write(":CHAN1:DISP ON")
    scope.write(":CHAN1:DISP ON")

    # Setting AC Coupling on Signal Channel...
    scope.write(":CHAN1:COUP AC")

    # Setting the Scale...
    scope.write(":CHAN1:SCAL 0.03")
    scope.write(":CHAN2:SCAL 2")

    # Setting the offset...
    scope.write(":CHAN1:OFFS -0.050")
    scope.write(":CHAN2:OFFS -7")

    # Setting the Acquisition Properties...
    scope.write(":ACQ:MDEP 12000")
    scope.write(":ACQ:TYPE NORM")

    # sampling_rate = float(scope.query(":ACQ:SRAT?"))

    # Setting the timescale properties...
    scope.write(":TIM:MAIN:SCAL 0.000002")
    scope.write(":TIM:MAIN:OFFS 0.000008")

    # Setting Trigger Mode...
    scope.write(":TRIG:MODE EDGE")

    # Setting Trigger Mode to Channel 2 Edge...
    scope.write(":TRIG:EDG:SOUR CHAN2")

    # Setting Trigger Mode to Positive Edge...
    scope.write(":TRIG:EDG:SLOP POS")

    # Setting Trigger Level...
    scope.write(":TRIG:EDG:LEV 1.98")

    # Setting Trigger Coupling: DC
    scope.write(":TRIG:COUP DC")

    # Setting Trigger to Single Mode...
    scope.write(":TRIG:SWE SING")

    # Setting the waveform for acquisition...
    scope.write(":WAV:SOUR CHAN1")
    scope.write(":WAV:MODE RAW")
    scope.write(":WAV:FORM ASCII")

    # Setting waveform startpoint...
    scope.write(":WAV:STAR 1")

    # Setting waveform endpoint...
    scope.write(":WAV:STOP 12000")


def arm_scope(scope):
    # Set the scope in Single Mode...
    scope.write(":SING")
    print("Waiting to be armed")

    time.sleep(2)

    print("Wait completed...")


def communicate_with_target(ser):
    ser.write(fixed_bytes)

    # Generate 16 random bytes

    plaintext_bytes = os.urandom(16)
    ser.write(plaintext_bytes)

    # Convert the received bytes to a byte array and take the last 16 bytes

    plaintext_bytes = bytearray(plaintext_bytes)
    plaintext_value = 0
    i = 0
    for byte in plaintext_bytes:
        plaintext_value = plaintext_value | (byte << (8*i))
        i = i+1

    # Read the 20 bytes from the serial port
    ciphertext_bytes = ser.read(20)

    # Convert the received bytes to a byte array and take the last 16 bytes

    ciphertext_bytes = bytearray(ciphertext_bytes)[-16:]
    ciphertext_value = 0
    i = 0

    for byte in ciphertext_bytes:
        ciphertext_value = ciphertext_value | (byte << (8*i))
        i = i+1

    # Print the ciphertext value in hexadecimal format

    print("Plaintext Value (Hex): {}, Ciphertext Value (Hex): {}".format(hex(plaintext_value), hex(ciphertext_value)))

    return plaintext_value, ciphertext_value


def store_trace(scope):
    # Now, you need to store the trace...

    scope.write(":WAV:SOUR CHAN1")
    scope.write(":WAV:MODE RAW")
    scope.write(":WAV:FORM BYTE")
    scope.write(":WAV:STAR 1")
    scope.write(":WAV:STOP 12000")
    # rawdata = scope.query(":WAV:DATA? CHAN1")[10:]
    rawdata = scope.query(":WAV:DATA?")[10:]
    data_size = len(rawdata)
    timescale = []

    for i in range(0, data_size):
        timescale.append(i)

    plot.plot(timescale, rawdata)
    plot.show()

    return rawdata


def string_to_numpy_array(input_string):
    # Split the input string by commas and strip any leading/trailing whitespaces

    numbers_str = input_string.split(',')
    numbers_str = [num.strip() for num in numbers_str]

    # Convert the string numbers to floating-point numbers
    numbers_float = [float(num) for num in numbers_str]

    # Convert the list of floating-point numbers to a NumPy array
    result_array = np.array(numbers_float)
    return result_array


# Main Function Starts here...

def main():
    # Connect to target...

    ser_target = connect_to_target()
    scope = connect_to_scope()
    configure_scope(scope)
    num_traces = 10000000

    for i in range(0, num_traces):
        arm_scope(scope)
        plaintext, ciphertext = communicate_with_target(ser_target)

        trace_got = store_trace(scope)


main()