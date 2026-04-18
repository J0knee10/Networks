# from smartcard.System import readers
# from smartcard.util import toHexString
#
# # MIFARE Ultralight commands
# CMD_GET_UID = [0x00, 0x00, 0x00, 0x00, 0x00]
#
# CMD_GET_PURSE_FILE = [0x90, 0x32, 0x03, 0x00, 0x00]
#
# CMD_GET_TRANSACTION_LOG = [0xFF, 0xB0, 0x00, 0x04, 0x04]
#
# # Function to send APDU commands to the card
# def send_apdu(connection, apdu_cmd):
#     data, sw1, sw2 = connection.transmit(apdu_cmd)
#     response = toHexString(data)
#     status_code = "SW1: {:02X}, SW2: {:02X}".format(sw1, sw2)
#     return response, status_code
#
# def main():
#     # Get all available smart card readers
#     card_readers = readers()
#
#     if not card_readers:
#         print("No smart card readers found.")
#         return
#
#     print("Available smart card readers:")
#     for reader in card_readers:
#         print(reader)
#
#     # Select the reader you want to connect to....
#     reader = card_readers[1]
#
#     # Connect to the selected reader
#     connection = reader.createConnection()
#     connection.connect()
#
#     # Send command to get UID
#     response, status_code = send_apdu(connection, CMD_GET_TRANSACTION_LOG)
#
#     if status_code == "SW1: 90, SW2: 00":
#         # Extract UID from the response
#         uid = response
#         print("Response:", uid)
#     else:
#         print("Failed to retrieve UID.")
#
#     # Disconnect from the reader
#     connection.disconnect()
#
# if __name__ == "__main__":
#     main()

from smartcard.System import readers
from smartcard.util import toHexString

# Commands
CMD_GET_PURSE_FILE = [0x90, 0x32, 0x03, 0x00, 0x00]

# Function to send APDU commands to the card
def send_apdu(connection, apdu_cmd):
    data, sw1, sw2 = connection.transmit(apdu_cmd)
    response = toHexString(data)
    status_code = "SW1: {:02X}, SW2: {:02X}".format(sw1, sw2)
    return data, response, status_code

def get_transaction_log_command(page, length):
    return [0xFF, 0xB0, 0x00, page, length]

def main():
    # Get all available smart card readers
    card_readers = readers()

    if not card_readers:
        print("No smart card readers found.")
        return

    print("Available smart card readers:")
    for i, reader in enumerate(card_readers):
        print(f"{i}: {reader}")

    # Select reader (still hardcoded for now)
    reader = card_readers[1]

    # Connect to the selected reader
    connection = reader.createConnection()
    connection.connect()

    try:
        while True:
            print("\nSelect operation:")
            print("1 - Read Transaction Log")
            print("2 - Read Purse Balance")
            print("0 - Exit")

            choice = input("Enter choice: ")

            if choice == "1":
                # Transaction log
                page = int(input("Enter starting page (0-255): "))
                length = int(input("Enter length (1-255): "))

                if not (0 <= page <= 255 and 1 <= length <= 255):
                    print("Invalid input.")
                    continue

                cmd = get_transaction_log_command(page, length)
                print(f"Sending APDU: {cmd}")

                data, response, status_code = send_apdu(connection, cmd)

                if status_code == "SW1: 90, SW2: 00":
                    print("Response:", response)
                else:
                    print("Failed:", status_code)

            elif choice == "2":
                # Purse balance
                print(f"Sending APDU: {CMD_GET_PURSE_FILE}")

                data, response, status_code = send_apdu(connection, CMD_GET_PURSE_FILE)

                if status_code == "SW1: 90, SW2: 00":
                    print("Raw Response:", response)

                    # Extract bytes 3 to 5 (index 2,3,4)
                    if len(data) >= 5:
                        balance_bytes = data[2:5]

                        # Hex representation
                        balance_hex = ' '.join(f"{b:02X}" for b in balance_bytes)

                        # Decimal (big-endian)
                        balance_decimal = (balance_bytes[0] << 16) | \
                                          (balance_bytes[1] << 8) | \
                                           balance_bytes[2]

                        print("Balance in hex:", balance_hex)
                        print("Balance in decimal:", balance_decimal)
                    else:
                        print("Response too short to extract balance.")

                else:
                    print("Failed:", status_code)

            elif choice == "0":
                print("Exiting...")
                break

            else:
                print("Invalid choice.")

    finally:
        # Disconnect from the reader
        connection.disconnect()

if __name__ == "__main__":
    main()